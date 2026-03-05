package com.sprint.acdsimulator.service;

import com.sprint.acdsimulator.model.AcdMetrics;
import com.sprint.acdsimulator.model.AgentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Assembles an AcdMetrics snapshot from QueueManagerService + AgentRegistryService.
 * Used by both the REST controller and the WebSocket broadcaster.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final QueueManagerService queueManager;
    private final AgentRegistryService agentRegistry;

    public AcdMetrics snapshot() {
        return AcdMetrics.builder()
                .totalCallsReceived(queueManager.getTotalReceived())
                .totalCallsCompleted(queueManager.getTotalCompleted())
                .totalCallsAbandoned(queueManager.getTotalAbandoned())
                .totalCallsDropped(queueManager.getTotalDropped())
                .currentQueueDepth(queueManager.getQueueSize())
                .availableAgents((int) agentRegistry.countByStatus(AgentStatus.AVAILABLE))
                .busyAgents((int) agentRegistry.countByStatus(AgentStatus.BUSY))
                .avgWaitTimeMs(queueManager.getAvgWaitTimeMs())
                .snapshotAt(Instant.now())
                .build();
    }
}

