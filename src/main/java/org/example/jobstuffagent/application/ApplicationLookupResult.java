package org.example.jobstuffagent.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The deterministic result of applying lookup criteria to authoritative data.
 */
public record ApplicationLookupResult(
        AgentClassification classification,
        List<UUID> selectedApplicationIds,
        List<ApplicationLookupError> errors,
        String summary) {

    public ApplicationLookupResult {
        Objects.requireNonNull(classification, "classification must not be null");
        selectedApplicationIds = List.copyOf(Objects.requireNonNull(
                selectedApplicationIds, "selectedApplicationIds must not be null"));
        errors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }

}
