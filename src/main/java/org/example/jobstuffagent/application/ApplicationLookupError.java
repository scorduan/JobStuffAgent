package org.example.jobstuffagent.application;

import java.util.Objects;

public record ApplicationLookupError(
        ApplicationLookupErrorCode code,
        String message) {

    public ApplicationLookupError {
        Objects.requireNonNull(code, "code must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.trim();
    }
}
