package org.example.jobstuffagent.application;

import java.util.List;

/**
 * Proposals accepted by deterministic validation and safe to send to a tool executor.
 */
public record AgentValidatedPlan(
        List<AgentProposal> proposals,
        String summary) {

    public AgentValidatedPlan {
        proposals = List.copyOf(proposals);
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
