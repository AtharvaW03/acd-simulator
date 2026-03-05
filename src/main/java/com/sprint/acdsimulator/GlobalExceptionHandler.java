package com.sprint.acdsimulator;

import com.sprint.acdsimulator.exception.AgentNotFoundException;
import com.sprint.acdsimulator.exception.QueueFullException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 📚 LESSON — @RestControllerAdvice + ProblemDetail (RFC 9457):
 *
 * @RestControllerAdvice is a specialisation of @ControllerAdvice that:
 *   - Intercepts ALL exceptions thrown from any @RestController
 *   - Lets you map exception types → HTTP status codes in one central place
 *   - Eliminates try/catch blocks in your controllers
 *
 * Without this class, an unhandled QueueFullException would return a generic
 * 500 Internal Server Error with a confusing HTML error page.
 * With this, it returns a clean JSON 503 that clients can handle.
 *
 * ProblemDetail is the Spring 6 implementation of RFC 9457 "Problem Details for HTTP APIs":
 *   {
 *     "type": "https://api.acdsimulator.com/errors/queue-full",
 *     "title": "Service Unavailable",
 *     "status": 503,
 *     "detail": "Queue is at capacity (10000). Try again later.",
 *     "instance": "/api/v1/calls"
 *   }
 * This is the standardised way to return structured error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Queue is full → 503 Service Unavailable (server is too busy, client should retry) */
    @ExceptionHandler(QueueFullException.class)
    public ProblemDetail handleQueueFull(QueueFullException ex, WebRequest request) {
        log.warn("[Error] Queue full: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("Call Queue Full");
        pd.setType(URI.create("https://acdsimulator.local/errors/queue-full"));
        return pd;
    }

    /** Agent not found → 404 Not Found */
    @ExceptionHandler(AgentNotFoundException.class)
    public ProblemDetail handleAgentNotFound(AgentNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Agent Not Found");
        return pd;
    }

    /**
     * Rate limit exceeded → 429 Too Many Requests.
     * Resilience4j throws this when the @RateLimiter budget is exhausted.
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ProblemDetail handleRateLimit(RequestNotPermitted ex) {
        log.warn("[Error] Rate limit exceeded: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Slow down and retry.");
        pd.setTitle("Too Many Requests");
        return pd;
    }

    /**
     * Bean Validation failure → 400 Bad Request with per-field error messages.
     * Triggered by @Valid on @RequestBody.
     *
     * 📚 Example response:
     * {
     *   "status": 400,
     *   "title": "Validation Failed",
     *   "detail": "1 constraint(s) violated",
     *   "errors": { "callerId": "Caller ID must not be blank" }
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid",
                        (a, b) -> a  // keep first if duplicate field
                ));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                fieldErrors.size() + " constraint(s) violated");
        pd.setTitle("Validation Failed");
        pd.setProperty("errors", fieldErrors);
        return pd;
    }

    /** Catch-all for anything we didn't predict → 500 Internal Server Error */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, WebRequest request) {
        log.error("[Error] Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setTitle("Internal Server Error");
        return pd;
    }
}

