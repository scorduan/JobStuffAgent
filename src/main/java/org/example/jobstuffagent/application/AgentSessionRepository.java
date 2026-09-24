package org.example.jobstuffagent.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for bounded workflow execution attempts.
 */
public interface AgentSessionRepository {

    AgentSession save(AgentSession session);

    Optional<AgentSession> findById(UUID id);

    List<AgentSession> findAll();
}
