package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.ApplicationStatus;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A bounded action requested by a model or deterministic planner. It is not executable by itself.
 */
public record AgentProposal(
        AgentAction action,
        UUID applicationId,
        ApplicationStatus targetStatus,
        LocalDate effectiveDate,
        String source,
        String note) {

    public AgentProposal {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        source = requireText(source, "source");
        note = note == null || note.isBlank() ? null : note.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
