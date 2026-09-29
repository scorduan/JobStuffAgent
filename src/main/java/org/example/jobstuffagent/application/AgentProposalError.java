package org.example.jobstuffagent.application;

import java.util.Objects;

public record AgentProposalError(
        AgentProposalErrorCode code,
        String message) {

    public AgentProposalError {
        Objects.requireNonNull(code, "code must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.trim();
    }
}
