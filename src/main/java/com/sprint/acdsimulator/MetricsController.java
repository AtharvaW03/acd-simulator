package com.sprint.acdsimulator;

import com.sprint.acdsimulator.model.AcdMetrics;
import com.sprint.acdsimulator.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the current ACD metrics snapshot.
 * The same data also flows via WebSocket every 2 seconds.
 */
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final DashboardService dashboardService;

    @GetMapping("/snapshot")
    public AcdMetrics snapshot() {
        return dashboardService.snapshot();
    }
}

