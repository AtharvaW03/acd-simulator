package com.sprint.acdsimulator.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * 📚 LESSON — DTOs (Data Transfer Objects):
 *
 * A DTO is a plain object whose only job is to carry data across a boundary —
 * for example from your service layer to a REST response or a WebSocket message.
 *
 * Why not just return the internal Agent/Call objects directly?
 *   - Internal objects may have fields you don't want to expose (e.g. Atomic wrappers)
 *   - Internal objects change shape as you refactor; DTOs are a stable contract
 *   - Jackson serialises @Builder objects cleanly with @JsonDeserialize(builder=...)
 *     but simple @Data classes with a no-arg constructor work out-of-the-box.
 */
@Data
@Builder
public class AcdMetrics {

    private final long totalCallsReceived;
    private final long totalCallsCompleted;
    private final long totalCallsAbandoned;
    private final long totalCallsDropped;   // overflow — queue was full at intake
    private final int  currentQueueDepth;
    private final int  availableAgents;
    private final int  busyAgents;
    private final double avgWaitTimeMs;     // rolling average wait time in milliseconds
    private final Instant snapshotAt;       // when this snapshot was taken
}

