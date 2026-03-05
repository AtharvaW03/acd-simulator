package com.sprint.acdsimulator.service;

import com.sprint.acdsimulator.model.Agent;
import com.sprint.acdsimulator.model.AgentStatus;
import com.sprint.acdsimulator.exception.AgentNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 📚 LESSON — ConcurrentHashMap vs HashMap vs synchronized Map:
 *
 * HashMap:
 *   - NOT thread-safe. If two threads write simultaneously → ConcurrentModificationException
 *     or silent data corruption.
 *
 * Collections.synchronizedMap(new HashMap<>()):
 *   - Thread-safe but puts a single lock on the ENTIRE map.
 *   - Every read AND write blocks all other threads → bottleneck at high call rates.
 *
 * ConcurrentHashMap:
 *   - Thread-safe with SEGMENT locking: only the bucket being written is locked.
 *   - Reads are entirely lock-free (non-blocking).
 *   - Under 50 concurrent threads, throughput is ~50x better than synchronizedMap. ✅
 *
 * Rule: always use ConcurrentHashMap when multiple threads access a shared Map.
 */
@Slf4j
@Service
public class AgentRegistryService {

    // UUID → Agent. ConcurrentHashMap = lock-free reads + fine-grained writes
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    // ── CRUD ──────────────────────────────────────────────────────────────────────────

    public Agent registerAgent(String name) {
        String id = "AGT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Agent agent = Agent.builder()
                .id(id)
                .name(name)
                .build();
        agents.put(id, agent);
        log.info("[Registry] + Agent registered: {} ({})", name, id);
        return agent;
    }

    public Agent getAgent(String id) {
        Agent a = agents.get(id);
        if (a == null) throw new AgentNotFoundException(id);
        return a;
    }

    public Collection<Agent> getAllAgents() {
        return agents.values();
    }

    public void removeAgent(String id) {
        agents.remove(id);
        log.info("[Registry] - Agent removed: {}", id);
    }

    // ── Routing helpers ───────────────────────────────────────────────────────────────

    /**
     * Finds an AVAILABLE agent and atomically claims them for the given callId.
     *
     * 📚 LESSON — tryClaimForCall uses CAS (Compare-And-Swap):
     *   status.compareAndSet(AVAILABLE, BUSY) succeeds ONLY if the current value is
     *   still AVAILABLE at the exact moment of the swap. If another routing thread
     *   claimed the agent a nanosecond earlier, our CAS fails and we try the next agent.
     *   This is lock-free coordination — no synchronized block needed.
     *
     * Returns an Optional — the caller must handle the empty case (no agents free).
     */
    public Optional<Agent> findAndClaimAgent(String callId) {
        for (Agent agent : agents.values()) {
            if (agent.tryClaimForCall(callId)) {
                log.debug("[Registry] Agent {} claimed for call {}", agent.getName(), callId);
                return Optional.of(agent);
            }
        }
        return Optional.empty();
    }

    // ── Stats ─────────────────────────────────────────────────────────────────────────

    public long countByStatus(AgentStatus status) {
        return agents.values().stream()
                .filter(a -> a.getStatusValue() == status)
                .count();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", agents.size());
        stats.put("available", countByStatus(AgentStatus.AVAILABLE));
        stats.put("busy", countByStatus(AgentStatus.BUSY));
        stats.put("offline", countByStatus(AgentStatus.OFFLINE));
        return stats;
    }
}

