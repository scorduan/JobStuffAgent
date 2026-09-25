package org.example.jobstuffagent.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A user-visible thread that can contain several bounded agent sessions.
 */
public final class Conversation {

    private final UUID id;
    private final Instant createdAt;
    private final List<UUID> sessionIds = new ArrayList<>();

    public Conversation(UUID id, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<UUID> sessionIds() {
        return List.copyOf(sessionIds);
    }

    public void addSession(UUID sessionId) {
        sessionIds.add(Objects.requireNonNull(sessionId, "sessionId must not be null"));
    }
}
