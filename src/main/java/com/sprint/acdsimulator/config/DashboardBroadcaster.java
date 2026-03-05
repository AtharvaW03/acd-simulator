package com.sprint.acdsimulator.config;

import com.sprint.acdsimulator.model.AcdMetrics;
import com.sprint.acdsimulator.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 📚 LESSON — SimpMessagingTemplate (Server-push over WebSocket):
 *
 * SimpMessagingTemplate is Spring's way to PUSH messages from the server.
 * Think of it as a REST template but for WebSocket destinations.
 *
 *   convertAndSend("/topic/dashboard", payload)
 *     → serialises `payload` to JSON (Jackson)
 *     → broadcasts to ALL clients subscribed to /topic/dashboard
 *
 * This runs on the @Scheduled thread (not an HTTP thread), so it doesn't block any requests.
 * Every 2 seconds all connected dashboards get a fresh metrics snapshot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final DashboardService dashboardService;

    @Scheduled(fixedRate = 2000)  // every 2 seconds
    public void broadcastMetrics() {
        AcdMetrics snapshot = dashboardService.snapshot();
        messagingTemplate.convertAndSend("/topic/dashboard", snapshot);
        log.debug("[WS] Broadcast: queueDepth={} available={} busy={}",
                snapshot.getCurrentQueueDepth(),
                snapshot.getAvailableAgents(),
                snapshot.getBusyAgents());
    }
}

