package org.example.jobstuffagent.application;

import java.util.List;

/**
 * The planning-stage interpretation of lookup results and any next questions.
 */
public record AgentPlanningResult(
        List<AgentProposal> proposals,
        List<String> questions,
        String summary) {

    public AgentPlanningResult {
        proposals = List.copyOf(proposals);
        questions = List.copyOf(questions);
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }

    public boolean needsInput() {
        return !questions.isEmpty();
    }
}
