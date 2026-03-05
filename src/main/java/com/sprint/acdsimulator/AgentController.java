package com.sprint.acdsimulator;

import com.sprint.acdsimulator.dto.AgentRequest;
import com.sprint.acdsimulator.dto.AgentResponse;
import com.sprint.acdsimulator.model.Agent;
import com.sprint.acdsimulator.model.AgentStatus;
import com.sprint.acdsimulator.service.AgentRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for managing agents.
 *
 * POST   /api/v1/agents              — register a new agent
 * GET    /api/v1/agents              — list all agents
 * GET    /api/v1/agents/{id}         — get a specific agent
 * PATCH  /api/v1/agents/{id}/status  — set status (AVAILABLE / OFFLINE)
 * DELETE /api/v1/agents/{id}         — remove agent
 * GET    /api/v1/agents/stats        — summary counts
 *
 * 📚 LESSON — @RequestMapping at class level:
 * Setting the base path on the class means all methods inherit it.
 * You only need to specify the specific suffix on each method.
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistryService agentRegistry;

    @PostMapping
    public ResponseEntity<AgentResponse> registerAgent(@Valid @RequestBody AgentRequest request) {
        Agent agent = agentRegistry.registerAgent(request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(agent));
    }

    @GetMapping
    public List<AgentResponse> listAgents() {
        return agentRegistry.getAllAgents().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public AgentResponse getAgent(@PathVariable String id) {
        return toResponse(agentRegistry.getAgent(id));
    }

    /**
     * PATCH is the correct verb for a partial update.
     * PUT would replace the entire resource; we only want to flip the status.
     *
     * 📚 LESSON — @PathVariable vs @RequestParam:
     *   /agents/AGT-123/status  → @PathVariable("id") String id  (part of the URL path)
     *   /agents?status=OFFLINE  → @RequestParam String status     (query string)
     * Path variables are preferred for resource identifiers; query params for filters/options.
     */
    @PatchMapping("/{id}/status")
    public AgentResponse updateStatus(
            @PathVariable String id,
            @RequestParam AgentStatus status) {

        Agent agent = agentRegistry.getAgent(id);
        agent.getStatus().set(status);
        return toResponse(agent);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeAgent(@PathVariable String id) {
        agentRegistry.removeAgent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public Object getStats() {
        return agentRegistry.getStats();
    }

    // ── Mapper ────────────────────────────────────────────────────────────────────────

    private AgentResponse toResponse(Agent agent) {
        return AgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .status(agent.getStatusValue())
                .currentCallId(agent.getCurrentCallId().get())
                .callsHandled(agent.getCallsHandled().get())
                .build();
    }
}

