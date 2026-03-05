package com.sprint.acdsimulator.exception;

/**
 * Thrown when an agent with the given ID is not found in the registry.
 * Maps to 404 Not Found in GlobalExceptionHandler.
 */
public class AgentNotFoundException extends RuntimeException {
    public AgentNotFoundException(String agentId) {
        super("Agent not found: " + agentId);
    }
}

