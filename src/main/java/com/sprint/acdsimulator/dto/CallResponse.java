package com.sprint.acdsimulator.dto;

import com.sprint.acdsimulator.model.CallStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 📚 LESSON — HTTP 202 Accepted:
 *
 * When a call arrives, we don't process it synchronously (that would block the HTTP thread
 * while we wait for an agent — terrible for throughput). Instead we:
 *   1. Validate the request
 *   2. Drop it on the queue (nanoseconds)
 *   3. Return 202 Accepted immediately
 *
 * 202 means: "I got your request, it's in the system, but I haven't processed it yet."
 * The client gets an ID to check status later — this is the "fire-and-forget" pattern.
 *
 * Contrast with 200 OK (synchronous: done) and 201 Created (synchronous: resource made).
 */
@Data
@Builder
public class CallResponse {

    private final String callId;
    private final String callerId;
    private final int priority;
    private final CallStatus status;
    private final LocalDateTime receivedAt;
    private final int currentQueueDepth;
    private final String message;
}

