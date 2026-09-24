package org.example.jobstuffagent.adapter.persistence;

import org.example.jobstuffagent.application.AgentSession;
import org.example.jobstuffagent.application.AgentSessionRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A development-only agent-session store. Its contents disappear when the process stops.
 */
@Repository
public class InMemoryAgentSessionRepository implements AgentSessionRepository {

    private final ConcurrentMap<UUID, AgentSession> sessions = new ConcurrentHashMap<>();

    @Override
    public AgentSession save(AgentSession session) {
        sessions.put(session.id(), session);
        return session;
    }

    @Override
    public Optional<AgentSession> findById(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public List<AgentSession> findAll() {
        return sessions.values().stream()
                .sorted(Comparator.comparing(AgentSession::startedAt).thenComparing(AgentSession::id))
                .toList();
    }
}
