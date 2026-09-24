package org.example.jobstuffagent.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An immutable audit entry for one accepted application lifecycle transition.
 */
public record ApplicationTransition(
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        LocalDate effectiveDate,
        Instant recordedAt,
        String source,
        String note) {

    public ApplicationTransition {
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        source = requireText(source, "source");
        note = normalizeOptionalText(note);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
