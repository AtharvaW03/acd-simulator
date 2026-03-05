package com.sprint.acdsimulator.model;

/**
 * 📚 LESSON — Enums with fields:
 * Enums can hold data and methods, just like classes.
 * Here AgentStatus carries a human-readable label used in API responses.
 */
public enum AgentStatus {
    AVAILABLE("Available"),
    BUSY("Busy"),
    OFFLINE("Offline");

    private final String label;

    AgentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

