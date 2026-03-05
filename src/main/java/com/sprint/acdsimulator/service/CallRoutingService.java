package com.sprint.acdsimulator.service;

import com.sprint.acdsimulator.Call;
import com.sprint.acdsimulator.model.Agent;
import com.sprint.acdsimulator.model.CallStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 📚 LESSON — @Scheduled:
 *
 * Instead of having agent threads block on queue.take(), we use a dedicated
 * ROUTING LOOP: a scheduler that fires every N milliseconds, checks the queue,
 * and dispatches calls to agents. This separation of concerns means:
 *   - The HTTP thread does nothing except validate + enqueue (fast)
 *   - The routing thread does nothing except dequeue + dispatch (fast)
 *   - Agent threads do nothing except simulate handling (slow — blocked on sleep)
 *
 * @Scheduled(fixedDelay = 50) means: wait 50ms AFTER the last run finishes before running again.
 * @Scheduled(fixedRate = 50)  means: run every 50ms regardless of how long the last run took.
 * fixedDelay is safer here — if routing takes longer than 50ms, we don't pile up.
 *
 * @EnableScheduling must be on a @Configuration class (see AsyncConfig) for @Scheduled to work.
 *
 * 📚 LESSON — @CircuitBreaker (Resilience4j):
 *
 * A circuit breaker wraps a method and tracks its success/failure rate.
 * States:
 *   CLOSED  — all calls pass through normally (healthy)
 *   OPEN    — too many failures; calls are short-circuited to the fallback immediately
 *   HALF-OPEN — after a wait, lets a few calls through to test if things recovered
 *
 * Why useful here: if the routing loop starts throwing (e.g. agent registry down),
 * instead of hammering a broken subsystem every 50ms, the circuit opens and the
 * fallback is called — preventing cascading failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallRoutingService {

    private final QueueManagerService queueManager;
    private final AgentRegistryService agentRegistry;

    @Value("${acd.routing.overflow-max:3}")
    private int maxOverflow;

    /**
     * Core routing loop — runs every 50ms.
     * Non-blocking poll (not .take()) because we don't want the scheduling thread to block.
     */
    @Scheduled(fixedDelayString = "${acd.routing.delay-ms:50}")
    @CircuitBreaker(name = "routingService", fallbackMethod = "routingFallback")
    public void routeNextCall() {
        // poll() returns null immediately if queue is empty — never blocks
        Call call = queueManager.getCallQueue().poll();
        if (call == null) return;

        call.setStatusValue(CallStatus.ROUTING);

        Optional<Agent> agentOpt = agentRegistry.findAndClaimAgent(call.getId());

        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            dispatchCallToAgent(call, agent);
        } else {
            handleOverflow(call);
        }
    }

    /**
     * Hands off the call to an agent on a virtual thread.
     *
     * 📚 LESSON — Virtual Threads (Java 21, Project Loom):
     *
     * A platform (OS) thread costs ~1MB of stack memory. A JVM can comfortably run
     * ~1,000 of them before memory pressure degrades performance.
     *
     * A virtual thread is managed by the JVM, not the OS. It costs ~1-10KB.
     * You can run MILLIONS of them simultaneously. When a virtual thread blocks
     * (e.g. Thread.sleep, I/O, DB query) the JVM parks it and reuses the carrier
     * (OS) thread for another virtual thread — zero wasted CPU.
     *
     * Thread.ofVirtual().start(runnable) — creates and immediately starts a virtual thread.
     * No thread pool needed — virtual threads ARE the pool.
     *
     * This is why we can simulate 50 agents with 50 virtual threads and handle
     * thousands of concurrent calls without the old 1-thread-per-call overhead.
     */
    private void dispatchCallToAgent(Call call, Agent agent) {
        call.setStatusValue(CallStatus.IN_PROGRESS);
        Instant startedAt = Instant.now();
        log.info("[Routing] → {} assigned to {} | priority={}", call.getId(), agent.getName(), call.getPriority());

        // Each call handling runs on its own virtual thread — lightweight!
        Thread.ofVirtual().name("agent-" + agent.getId()).start(() -> {
            try {
                // Simulate the actual call conversation (2-5 seconds)
                int durationMs = 2000 + (int)(Math.random() * 3000);
                Thread.sleep(durationMs);

                Instant completedAt = Instant.now();
                queueManager.recordCompleted(call, agent.getId(), startedAt, completedAt);
                agent.releaseCall();

                log.info("[Routing] ✓ {} completed by {} ({}ms)", call.getId(), agent.getName(), durationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Routing] Call {} interrupted on agent {}", call.getId(), agent.getName());
            }
        });
    }

    private void handleOverflow(Call call) {
        int overflows = call.getOverflowCount().incrementAndGet();
        if (overflows >= maxOverflow) {
            queueManager.recordAbandoned(call);
            log.warn("[Routing] ✗ {} abandoned after {} overflows", call.getId(), overflows);
        } else {
            call.setStatusValue(CallStatus.OVERFLOW);
            queueManager.addCall(call);  // re-queue
            log.warn("[Routing] ↻ {} re-queued (overflow #{})", call.getId(), overflows);
        }
    }

    /**
     * Fallback method invoked by the circuit breaker when routeNextCall() keeps failing.
     * Signature must match the protected method + a Throwable parameter.
     */
    @SuppressWarnings("unused")
    private void routingFallback(Throwable t) {
        log.error("[Routing] Circuit breaker OPEN — routing paused: {}", t.getMessage());
    }
}

