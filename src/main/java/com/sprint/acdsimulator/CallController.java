package com.sprint.acdsimulator;

import com.sprint.acdsimulator.dto.CallRequest;
import com.sprint.acdsimulator.dto.CallResponse;
import com.sprint.acdsimulator.exception.QueueFullException;
import com.sprint.acdsimulator.model.CallStatus;
import com.sprint.acdsimulator.service.QueueManagerService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 📚 LESSON — REST API Design Principles:
 *
 * OLD (bad) endpoint:  GET /call?id=123&priority=1
 *   - GET should never mutate state (this creates a call — that's a mutation)
 *   - Query params for complex data don't scale
 *   - No versioning (/v1/) — breaking the API later is painful
 *
 * NEW (good) design:
 *   POST /api/v1/calls         — create one call       → 202 Accepted
 *   POST /api/v1/calls/bulk    — create many calls     → 202 Accepted
 *   GET  /api/v1/calls/{id}    — check call status     → 200 OK
 *   GET  /api/v1/calls/stats   — queue stats           → 200 OK
 *
 * 📚 LESSON — @RateLimiter (Resilience4j):
 *
 * Configured in application.properties:
 *   resilience4j.ratelimiter.instances.callIngestion.limitForPeriod=5000
 *   resilience4j.ratelimiter.instances.callIngestion.limitRefreshPeriod=1s
 *
 * If more than 5000 calls/second arrive, the rate limiter throws RequestNotPermitted
 * which our GlobalExceptionHandler converts to HTTP 429 Too Many Requests.
 * This protects the queue from thundering herds and DoS attacks.
 *
 * 📚 LESSON — ResponseEntity<T>:
 *
 * ResponseEntity lets you control:
 *   - The response body (the <T>)
 *   - The HTTP status code
 *   - Response headers (e.g. Location header for 201 Created)
 *
 * ResponseEntity.accepted().body(dto)  →  HTTP 202 with JSON body
 * ResponseEntity.ok(dto)               →  HTTP 200 with JSON body
 * ResponseEntity.status(503).build()   →  HTTP 503 no body
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final QueueManagerService queueManager;

    /**
     * Ingest a single incoming call.
     * Returns 202 Accepted — the call is queued but not yet handled.
     */
    @PostMapping
    @RateLimiter(name = "callIngestion", fallbackMethod = "rateLimitFallback")
    public ResponseEntity<CallResponse> receiveCall(@Valid @RequestBody CallRequest request) {
        Call call = new Call(
                UUID.randomUUID().toString(),
                request.getPriority(),
                request.getCallerId()
        );
        queueManager.addCall(call);

        CallResponse response = CallResponse.builder()
                .callId(call.getId())
                .callerId(call.getCallerId())
                .priority(call.getPriority())
                .status(CallStatus.QUEUED)
                .receivedAt(call.getReceivedAt())
                .currentQueueDepth(queueManager.getQueueSize())
                .message("Call accepted and queued successfully")
                .build();

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Bulk ingest — accepts a list of calls in one HTTP request.
     * Useful for load testing and batch scenarios.
     *
     * 📚 Why bulk?
     * Each HTTP request has overhead (TCP handshake, headers, TLS).
     * Sending 100 calls in one request is ~100x cheaper than 100 separate requests.
     *
     * 📚 LESSON — Partial failure in batch APIs:
     * Unlike the single-call endpoint, a batch can partially succeed:
     * some calls queue fine, others hit the capacity limit. Instead of
     * failing the entire batch (HTTP 500), we handle each call individually
     * and return HTTP 207 Multi-Status if any were dropped.
     */
    @PostMapping("/bulk")
    @RateLimiter(name = "callIngestion", fallbackMethod = "bulkRateLimitFallback")
    public ResponseEntity<List<CallResponse>> receiveBulkCalls(
            @Valid @RequestBody List<@Valid CallRequest> requests) {

        boolean anyDropped = false;

        List<CallResponse> responses = requests.stream().map(request -> {
            Call call = new Call(
                    UUID.randomUUID().toString(),
                    request.getPriority(),
                    request.getCallerId()
            );
            try {
                queueManager.addCall(call);
                return CallResponse.builder()
                        .callId(call.getId())
                        .callerId(call.getCallerId())
                        .priority(call.getPriority())
                        .status(CallStatus.QUEUED)
                        .receivedAt(call.getReceivedAt())
                        .currentQueueDepth(queueManager.getQueueSize())
                        .message("Queued")
                        .build();
            } catch (QueueFullException e) {
                return CallResponse.builder()
                        .callId(call.getId())
                        .callerId(call.getCallerId())
                        .priority(call.getPriority())
                        .status(CallStatus.DROPPED)
                        .receivedAt(call.getReceivedAt())
                        .currentQueueDepth(queueManager.getQueueSize())
                        .message("Queue full — call dropped")
                        .build();
            }
        }).collect(Collectors.toList());

        // Check if any calls were dropped
        anyDropped = responses.stream()
                .anyMatch(r -> r.getStatus() == CallStatus.DROPPED);

        if (anyDropped) {
            // 207 Multi-Status: some succeeded, some didn't
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(responses);
        }
        return ResponseEntity.accepted().body(responses);
    }

    /**
     * Returns current queue and throughput statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(java.util.Map.of(
                "queueDepth",       queueManager.getQueueSize(),
                "totalReceived",    queueManager.getTotalReceived(),
                "totalCompleted",   queueManager.getTotalCompleted(),
                "totalAbandoned",   queueManager.getTotalAbandoned(),
                "totalDropped",     queueManager.getTotalDropped(),
                "avgWaitTimeMs",    queueManager.getAvgWaitTimeMs()
        ));
    }

    // ── Rate limiter fallbacks ────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    private ResponseEntity<CallResponse> rateLimitFallback(
            CallRequest request, Throwable t) {
        log.warn("[API] Rate limit exceeded for callerId={}", request.getCallerId());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(CallResponse.builder()
                        .message("Rate limit exceeded. Max 5000 calls/second.")
                        .status(CallStatus.ABANDONED)
                        .build());
    }

    @SuppressWarnings("unused")
    private ResponseEntity<List<CallResponse>> bulkRateLimitFallback(
            List<CallRequest> requests, Throwable t) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
}

