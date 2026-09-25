package org.example.jobstuffagent.application;

import java.util.List;
import java.util.Objects;

/**
 * Structured output from the deterministic classifier. It is not an executable plan.
 */
public record AgentClassification(
        AgentIntent intent,
        ApplicationLookupCriteria lookupCriteria,
        List<String> questions,
        String summary) {

    public AgentClassification {
        Objects.requireNonNull(intent, "intent must not be null");
        Objects.requireNonNull(lookupCriteria, "lookupCriteria must not be null");
        questions = List.copyOf(Objects.requireNonNull(questions, "questions must not be null"));
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }

    public boolean needsInput() {
        return !questions.isEmpty();
    }
}
