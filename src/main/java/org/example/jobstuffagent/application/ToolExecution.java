package org.example.jobstuffagent.application;

import java.time.Instant;
import java.util.Objects;

/**
 * The normalized result of one deterministic tool attempt.
 */
public record ToolExecution(
        AgentProposal proposal,
        ToolExecutionStatus status,
        Instant executedAt,
        String summary) {

    public ToolExecution {
        Objects.requireNonNull(proposal, "proposal must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
