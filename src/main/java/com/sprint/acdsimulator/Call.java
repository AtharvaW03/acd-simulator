package com.sprint.acdsimulator;

import com.sprint.acdsimulator.model.CallStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 📚 LESSON — AtomicReference vs synchronized:
 *
 * A Call object is touched by multiple threads at once:
 *   - The HTTP thread writes it into the queue
 *   - An agent thread reads it from the queue and updates its status
 *
 * If two threads write a plain field simultaneously → RACE CONDITION → corrupted data.
 *
 * Fix options:
 *   A) synchronized  — easy, but forces threads to queue up → kills throughput
 *   B) AtomicReference<T> — uses CPU-level Compare-And-Swap (CAS), lock-free, very fast ✅
 *
 * Rule of thumb: use Atomics for single-field thread safety, synchronized for multi-field operations.
 */
@Data
public class Call implements Comparable<Call> {

    // 'final' fields → Lombok includes them in the @RequiredArgsConstructor
    private final String id;
    private final int priority;   // 1 = VIP/High,  5 = Regular/Low
    private final String callerId; // phone number or customer identifier

    // Thread-safe mutable state — no 'synchronized' keyword needed
    private final AtomicReference<CallStatus> status = new AtomicReference<>(CallStatus.QUEUED);
    private final AtomicInteger overflowCount = new AtomicInteger(0);

    // Set at construction time; Lombok won't add it to the constructor (it has an initialiser)
    private final LocalDateTime receivedAt = LocalDateTime.now();

    /** Convenience getter — returns the enum value, not the AtomicReference wrapper */
    public CallStatus getStatusValue() {
        return status.get();
    }

    /** Convenience setter — thread-safe */
    public void setStatusValue(CallStatus newStatus) {
        status.set(newStatus);
    }

    @Override
    public int compareTo(Call other) {
        // Lower number = higher urgency (1=VIP beats 5=Regular)
        return Integer.compare(this.priority, other.priority);
    }
}
