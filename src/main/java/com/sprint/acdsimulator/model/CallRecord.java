package com.sprint.acdsimulator.model;

import lombok.Builder;
import lombok.Value;
import java.time.Duration;
import java.time.Instant;

/**
 * 📚 LESSON — @Value (Lombok immutable DTO):
 *
 * A completed call record should never change — it's a fact that happened.
 * @Value makes all fields final and generates no setters, enforcing immutability.
 * In modern Java you could use a `record` for this, but @Value integrates more
 * naturally with the rest of Lombok-annotated code.
 *
 * Records (Java 16+) equivalent:
 *   public record CallRecord(String callId, String agentId, ...) {}
 */
@Value
@Builder
public class CallRecord {

    String callId;
    String callerId;
    String agentId;
    int priority;
    Instant startedAt;
    Instant completedAt;
    Duration waitTime;   // time from QUEUED to IN_PROGRESS
    Duration handleTime; // time from IN_PROGRESS to COMPLETED
}

