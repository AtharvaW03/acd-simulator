package com.sprint.acdsimulator.model;

/**
 * 📚 LESSON — Enums in Java/Spring:
 * An enum is a fixed set of named constants. Using an enum for status (instead of raw
 * Strings like "queued", "busy") gives you:
 *   1. Compile-time safety — typos become compile errors, not runtime bugs
 *   2. Readability — CallStatus.QUEUED is self-documenting
 *   3. Jackson serialises enums as their name string automatically
 */
public enum CallStatus {
    QUEUED,       // sitting in the PriorityBlockingQueue
    ROUTING,      // being matched to an agent
    IN_PROGRESS,  // agent picked it up
    COMPLETED,    // call ended normally
    ABANDONED,    // no agent found after max overflows
    OVERFLOW,     // re-queued because no agent was available
    DROPPED       // rejected — queue was at capacity
}

