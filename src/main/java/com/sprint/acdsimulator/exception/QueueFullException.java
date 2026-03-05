package com.sprint.acdsimulator.exception;

/**
 * 📚 LESSON — Custom Exceptions:
 *
 * Throwing a specific exception type (QueueFullException) instead of a generic
 * RuntimeException gives you two advantages:
 *   1. Your GlobalExceptionHandler can catch it by exact type and return the
 *      right HTTP status (503 Service Unavailable — server is too busy right now)
 *   2. The name itself documents why the failure happened
 *
 * We extend RuntimeException (unchecked) so callers don't need a try/catch —
 * Spring's exception handler catches it at the controller layer automatically.
 */
public class QueueFullException extends RuntimeException {
    public QueueFullException(String message) {
        super(message);
    }
}

