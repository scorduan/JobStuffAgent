package org.example.jobstuffagent.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One bounded attempt to fulfill a user request within a conversation.
 */
public final class AgentSession {

    private final UUID id;
    private final UUID conversationId;
    private final String prompt;
    private final Instant startedAt;

    private AgentSessionStatus status = AgentSessionStatus.RECEIVED;
    private AgentClassification classification;
    private Instant completedAt;

    public AgentSession(UUID id, UUID conversationId, String prompt, Instant startedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        this.prompt = prompt.trim();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    }

    public UUID id() {
        return id;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public String prompt() {
        return prompt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public AgentSessionStatus status() {
        return status;
    }

    public Optional<AgentClassification> classification() {
        return Optional.ofNullable(classification);
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public void beginClassification() {
        requireStatus(AgentSessionStatus.RECEIVED);
        status = AgentSessionStatus.CLASSIFYING;
    }

    public void complete(AgentClassification classification, Instant completedAt) {
        requireStatus(AgentSessionStatus.CLASSIFYING);
        this.classification = Objects.requireNonNull(classification, "classification must not be null");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        status = classification.needsInput()
                ? AgentSessionStatus.COMPLETED_NEEDS_INPUT
                : AgentSessionStatus.COMPLETED;
    }

    private void requireStatus(AgentSessionStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException(
                    "Cannot change agent session from " + status + " while expecting " + expectedStatus);
        }
    }
}
