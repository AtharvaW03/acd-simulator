package com.sprint.acdsimulator.dto;

import com.sprint.acdsimulator.model.AgentStatus;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for agent endpoints — exposes only what the API consumer needs.
 */
@Data
@Builder
public class AgentResponse {
    private final String id;
    private final String name;
    private final AgentStatus status;
    private final String currentCallId;
    private final int callsHandled;
}

