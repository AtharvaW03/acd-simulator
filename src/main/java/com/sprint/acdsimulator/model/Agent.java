package com.sprint.acdsimulator.model;

import lombok.Builder;
import lombok.Data;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 📚 LESSON — @Builder vs @Data vs @Value:
 *
 * @Data     — generates: getters, setters, equals, hashCode, toString, requiredArgsConstructor.
 *             Use for mutable objects that have a natural "all-fields" constructor.
 *
 * @Value    — same as @Data but makes everything final + no setters.
 *             Use for immutable DTOs (records are even better in Java 16+).
 *
 * @Builder  — generates a fluent builder: Agent.builder().id("A1").name("Alice").build()
 *             Perfect when an object has many optional fields and you don't want a
 *             10-argument constructor nobody can read.
 *
 * Here we use @Data + @Builder together — fields are mutable (status changes as calls arrive)
 * but we construct the object via the readable builder pattern.
 */
@Data
@Builder
public class Agent {

    private final String id;
    private final String name;

    // Thread-safe mutable status — an agent flips between AVAILABLE and BUSY constantly
    @Builder.Default
    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.AVAILABLE);

    // Which call is this agent currently handling? null = idle
    @Builder.Default
    private final AtomicReference<String> currentCallId = new AtomicReference<>(null);

    // Running total of calls handled — AtomicInteger so we can do .incrementAndGet() safely
    @Builder.Default
    private final AtomicInteger callsHandled = new AtomicInteger(0);

    // ── Convenience wrappers (same pattern as Call.java) ──────────────────────────────

    public AgentStatus getStatusValue() {
        return status.get();
    }

    public boolean isAvailable() {
        return status.get() == AgentStatus.AVAILABLE;
    }

    /**
     * Atomically flip AVAILABLE → BUSY using CAS (Compare-And-Swap).
     * Returns true only if the agent was actually AVAILABLE at the moment of the swap.
     * This prevents two routing threads from assigning the same agent to two calls.
     */
    public boolean tryClaimForCall(String callId) {
        boolean claimed = status.compareAndSet(AgentStatus.AVAILABLE, AgentStatus.BUSY);
        if (claimed) {
            currentCallId.set(callId);
        }
        return claimed;
    }

    public void releaseCall() {
        currentCallId.set(null);
        callsHandled.incrementAndGet();
        status.set(AgentStatus.AVAILABLE);
    }
}

