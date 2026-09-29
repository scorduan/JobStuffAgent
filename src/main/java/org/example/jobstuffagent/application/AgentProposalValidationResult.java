package org.example.jobstuffagent.application;

import java.util.List;

public record AgentProposalValidationResult(
        AgentValidatedPlan validatedPlan,
        List<AgentProposalError> errors) {

    public AgentProposalValidationResult {
        errors = List.copyOf(errors);
        if ((validatedPlan == null) == errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "A validation result must contain either a validated plan or validation errors.");
        }
    }

    public boolean valid() {
        return validatedPlan != null && errors.isEmpty();
    }
}
