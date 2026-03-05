package com.sprint.acdsimulator.service;

import com.sprint.acdsimulator.Call;
import com.sprint.acdsimulator.exception.QueueFullException;
import com.sprint.acdsimulator.model.CallRecord;
import com.sprint.acdsimulator.model.CallStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 📚 LESSON — PriorityBlockingQueue (bounded):
 *
 * PriorityBlockingQueue is the secret weapon of this ACD system:
 *   - PRIORITY: sorts Calls by compareTo() — priority 1 (VIP) always dequeues before priority 5
 *   - BLOCKING:  .take() blocks the calling thread until a call is available (no busy-waiting)
 *   - THREAD-SAFE: multiple threads can add/remove simultaneously without data corruption
 *
 * BOUNDED queue (capacity from application.properties):
 *   .offer() returns false instead of throwing when full → we catch that and
 *   throw QueueFullException → GlobalExceptionHandler returns HTTP 503.
 *   An UNBOUNDED queue would accept calls indefinitely → OutOfMemoryError under load.
 *
 * 📚 LESSON — @Slf4j (Lombok logging):
 *   @Slf4j generates:  private static final Logger log = LoggerFactory.getLogger(ThisClass.class);
 *   Use log.info(), log.warn(), log.error() instead of System.out.println() in production code.
 *   Logging frameworks (Logback/Log4j2) let you:
 *     - Control verbosity per package without recompiling
 *     - Write to files, Elasticsearch, Splunk, etc.
 *     - Include thread name, timestamp, trace ID automatically
 *
 * 📚 LESSON — @Value("${...}"):
 *   Reads a value from application.properties at startup.
 *   The : sets a default if the property is absent — so the app still runs without the property.
 *
 * 📚 LESSON — Micrometer Metrics:
 *   Micrometer is the metrics facade (think: SLF4J but for metrics).
 *   You register a Counter/Gauge/Timer once in the constructor, then increment it.
 *   /actuator/prometheus exposes them for Prometheus to scrape every 15s.
 */
@Slf4j
@Service
public class QueueManagerService {

    // ── Configuration ─────────────────────────────────────────────────────────────────
    @Value("${acd.queue.capacity:10000}")
    private int queueCapacity;

    // ── Core data structure ────────────────────────────────────────────────────────────
    // initialCapacity=16 is internal array size hint, NOT the bound. We enforce the bound
    // manually via offer() + our own counter.
    private final PriorityBlockingQueue<Call> callQueue = new PriorityBlockingQueue<>(16);

    // ── Atomic counters — thread-safe without synchronized ────────────────────────────
    private final AtomicLong totalReceived  = new AtomicLong(0);
    private final AtomicLong totalDropped   = new AtomicLong(0);
    private final AtomicLong totalCompleted = new AtomicLong(0);
    private final AtomicLong totalAbandoned = new AtomicLong(0);

    // Running sum of wait times for average calculation
    private final AtomicLong totalWaitTimeMs = new AtomicLong(0);

    // Completed call history (thread-safe list — CopyOnWriteArrayList is safe for
    // concurrent reads; writes are rare so the copy-on-write cost is acceptable)
    private final List<CallRecord> callHistory = new CopyOnWriteArrayList<>();

    // ── Micrometer metrics ─────────────────────────────────────────────────────────────
    private final Counter receivedCounter;
    private final Counter droppedCounter;
    private final Counter completedCounter;
    private final Counter abandonedCounter;
    private final Timer   waitTimer;

    /**
     * 📚 LESSON — Constructor Injection:
     * Spring sees MeterRegistry in the constructor parameter and injects the bean
     * that was auto-configured when you added micrometer-registry-prometheus.
     * Constructor injection is preferred over @Autowired field injection because:
     *   - Fields are final → immutability
     *   - Easier to test (just pass a mock)
     *   - Fails fast at startup if the bean is missing
     */
    public QueueManagerService(MeterRegistry meterRegistry) {
        // Gauge = a value that can go up OR down (current queue size)
        Gauge.builder("acd.queue.depth", callQueue, PriorityBlockingQueue::size)
                .description("Current number of calls waiting in the queue")
                .register(meterRegistry);

        // Counter = a value that only ever increases (total calls received)
        receivedCounter  = Counter.builder("acd.calls.received").register(meterRegistry);
        droppedCounter   = Counter.builder("acd.calls.dropped").register(meterRegistry);
        completedCounter = Counter.builder("acd.calls.completed").register(meterRegistry);
        abandonedCounter = Counter.builder("acd.calls.abandoned").register(meterRegistry);

        // Timer = measures duration + counts; also records percentiles (p99, p95 etc.)
        waitTimer = Timer.builder("acd.calls.wait.time")
                .description("Time a call spends waiting in queue before an agent picks it up")
                .register(meterRegistry);
    }

    // ── Producer ───────────────────────────────────────────────────────────────────────

    /**
     * Accepts an incoming call.
     * Uses offer() (non-blocking) and enforces our own capacity bound.
     * Throws QueueFullException → caught by GlobalExceptionHandler → HTTP 503.
     */
    public void addCall(Call call) {
        if (callQueue.size() >= queueCapacity) {
            totalDropped.incrementAndGet();
            droppedCounter.increment();
            log.warn("[Queue] DROPPED call {} — queue at capacity ({})", call.getId(), queueCapacity);
            throw new QueueFullException(
                    "Queue is at capacity (" + queueCapacity + "). Try again later.");
        }
        callQueue.offer(call);
        totalReceived.incrementAndGet();
        receivedCounter.increment();
        log.info("[Queue] +QUEUED  {} | priority={} | depth={}", call.getId(), call.getPriority(), callQueue.size());
    }

    // ── Consumer ───────────────────────────────────────────────────────────────────────

    /**
     * Blocks until a call is available. Agents call this.
     * .take() is a blocking operation — the calling thread sleeps (no CPU spin) until
     * something is in the queue. This is the correct pattern for worker threads.
     */
    public Call getNextCall() throws InterruptedException {
        return callQueue.take();
    }

    // ── Completion recording ──────────────────────────────────────────────────────────

    public void recordCompleted(Call call, String agentId, Instant startedAt, Instant completedAt) {
        call.setStatusValue(CallStatus.COMPLETED);
        totalCompleted.incrementAndGet();
        completedCounter.increment();

        Duration wait = Duration.between(call.getReceivedAt(), startedAt);
        Duration handle = Duration.between(startedAt, completedAt);
        totalWaitTimeMs.addAndGet(wait.toMillis());
        waitTimer.record(wait.toMillis(), TimeUnit.MILLISECONDS);

        callHistory.add(CallRecord.builder()
                .callId(call.getId())
                .callerId(call.getCallerId())
                .agentId(agentId)
                .priority(call.getPriority())
                .startedAt(startedAt)
                .completedAt(completedAt)
                .waitTime(wait)
                .handleTime(handle)
                .build());

        log.info("[Queue] ✓DONE    {} | agent={} | wait={}ms | handle={}ms",
                call.getId(), agentId, wait.toMillis(), handle.toMillis());
    }

    public void recordAbandoned(Call call) {
        call.setStatusValue(CallStatus.ABANDONED);
        totalAbandoned.incrementAndGet();
        abandonedCounter.increment();
        log.warn("[Queue] ✗ABANDON {} | overflows={}", call.getId(), call.getOverflowCount().get());
    }

    // ── Metrics accessors ─────────────────────────────────────────────────────────────

    public int getQueueSize()         { return callQueue.size(); }
    public long getTotalReceived()    { return totalReceived.get(); }
    public long getTotalDropped()     { return totalDropped.get(); }
    public long getTotalCompleted()   { return totalCompleted.get(); }
    public long getTotalAbandoned()   { return totalAbandoned.get(); }

    /** Exposes the raw queue so CallRoutingService can poll() without blocking. */
    public PriorityBlockingQueue<Call> getCallQueue() { return callQueue; }

    public double getAvgWaitTimeMs() {
        long completed = totalCompleted.get();
        return completed == 0 ? 0.0 : (double) totalWaitTimeMs.get() / completed;
    }
}

