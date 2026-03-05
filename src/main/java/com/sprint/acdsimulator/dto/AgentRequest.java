package com.sprint.acdsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for registering a new agent via POST /api/v1/agents
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    @NotBlank(message = "Agent name must not be blank")
    private String name;
}

