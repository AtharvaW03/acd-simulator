package com.sprint.acdsimulator;

import com.sprint.acdsimulator.model.Agent;
import com.sprint.acdsimulator.service.AgentRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 📚 LESSON — CommandLineRunner:
 *
 * CommandLineRunner is a Spring Boot interface with one method: run(String... args).
 * Spring calls it AFTER the ApplicationContext is fully started — meaning all beans
 * are wired, @Value properties are injected, the web server is listening.
 * Use it for: seeding initial data, starting background workers, printing startup banners.
 *
 * 📚 LESSON — Virtual Threads (Java 21, Project Loom) deep dive:
 *
 * Old model (Platform Threads):
 *   new Thread(runnable).start()
 *   → creates an OS thread (~1MB stack, expensive to create, context-switch overhead)
 *   → JVM typically handles ~500-2000 before degrading
 *
 * New model (Virtual Threads):
 *   Thread.ofVirtual().name("agent-0").start(runnable)
 *   → JVM manages the thread, not the OS
 *   → costs ~1-10KB, millions can coexist
 *   → when the virtual thread BLOCKS (sleep, I/O, DB), the JVM unmounts it from
 *     the carrier (OS) thread and mounts another virtual thread — zero wasted CPU
 *
 * This means: 50 simulated agents each doing Thread.sleep(2000) only tie up 50 virtual
 * threads, NOT 50 OS threads. The app can handle far more concurrent calls.
 *
 * @Value("${acd.agents.count:10}"):
 *   Reads acd.agents.count from application.properties.
 *   Default is 10 if the property isn't set.
 *   Change it to 100 and you get 100 virtual-thread agents — zero extra memory cost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSimulationRunner implements CommandLineRunner {

    private final AgentRegistryService agentRegistry;

    @Value("${acd.agents.count:10}")
    private int agentCount;

    @Override
    public void run(String... args) {
        log.info("[Startup] Spinning up {} simulated agents on virtual threads...", agentCount);

        for (int i = 1; i <= agentCount; i++) {
            // Register the agent in the registry (gets an ID, starts as AVAILABLE)
            Agent agent = agentRegistry.registerAgent("Agent-" + String.format("%02d", i));

            /*
             * Virtual thread per agent — Thread.ofVirtual() is Java 21 API.
             * .name() sets a descriptive thread name (visible in profilers/thread dumps).
             * .start() launches immediately.
             *
             * The agent loop blocks on queueManager.getNextCall() — but since it's a
             * virtual thread, blocking only parks the virtual thread, not the OS thread.
             */
            Thread.ofVirtual()
                    .name("vt-agent-" + agent.getId())
                    .start(() -> log.info("[Agent] {} is clocked in (virtual thread)", agent.getName()));
        }

        log.info("[Startup] {} agents registered. CallRoutingService will dispatch calls via @Scheduled.", agentCount);
        log.info("[Startup] API ready:");
        log.info("[Startup]   POST /api/v1/calls         — ingest a call");
        log.info("[Startup]   POST /api/v1/calls/bulk    — ingest many calls");
        log.info("[Startup]   GET  /api/v1/calls/stats   — queue stats");
        log.info("[Startup]   POST /api/v1/agents        — register an agent");
        log.info("[Startup]   GET  /api/v1/agents        — list agents");
        log.info("[Startup]   GET  /api/v1/metrics/snapshot — ACD snapshot");
        log.info("[Startup]   GET  /actuator/health      — health check");
        log.info("[Startup]   WS   /ws-acd               — real-time dashboard");
    }
}

