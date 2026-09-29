package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministically validates proposed actions before any domain mutation is attempted.
 */
@Component
public class AgentProposalValidator {

    private final JobApplicationService jobApplicationService;

    public AgentProposalValidator(JobApplicationService jobApplicationService) {
        this.jobApplicationService = Objects.requireNonNull(
                jobApplicationService, "jobApplicationService must not be null");
    }

    public AgentProposalValidationResult validate(
            List<AgentProposal> proposals,
            List<UUID> selectedApplicationIds) {
        Objects.requireNonNull(proposals, "proposals must not be null");
        Objects.requireNonNull(selectedApplicationIds, "selectedApplicationIds must not be null");

        if (proposals.isEmpty()) {
            return invalid(new AgentProposalError(
                    AgentProposalErrorCode.EMPTY_PROPOSAL_SET,
                    "At least one proposal is required."));
        }
        if (proposals.size() > 1) {
            return invalid(new AgentProposalError(
                    AgentProposalErrorCode.MULTIPLE_PROPOSALS_NOT_SUPPORTED,
                    "Only one mutation proposal may be executed in a phase-four plan."));
        }

        Set<UUID> selectedIds = Set.copyOf(selectedApplicationIds);
        List<AgentProposalError> errors = proposals.stream()
                .flatMap(proposal -> validateProposal(proposal, selectedIds).stream())
                .toList();
        if (!errors.isEmpty()) {
            return new AgentProposalValidationResult(null, errors);
        }

        return new AgentProposalValidationResult(
                new AgentValidatedPlan(proposals, "Validated " + proposals.size() + " proposal(s)."),
                List.of());
    }

    private List<AgentProposalError> validateProposal(
            AgentProposal proposal,
            Set<UUID> selectedIds) {
        if (proposal.action() != AgentAction.TRANSITION_APPLICATION_STATUS) {
            return List.of(new AgentProposalError(
                    AgentProposalErrorCode.UNSUPPORTED_ACTION,
                    "The proposed action is not supported."));
        }
        if (!selectedIds.contains(proposal.applicationId())) {
            return List.of(new AgentProposalError(
                    AgentProposalErrorCode.APPLICATION_NOT_SELECTED,
                    "The proposal targets an application outside the selected records."));
        }
        JobApplication application = jobApplicationService.findById(proposal.applicationId()).orElse(null);
        if (application == null) {
            return List.of(new AgentProposalError(
                    AgentProposalErrorCode.APPLICATION_NOT_FOUND,
                    "The proposed application no longer exists."));
        }
        if (!application.canTransitionTo(proposal.targetStatus())) {
            return List.of(new AgentProposalError(
                    AgentProposalErrorCode.INVALID_LIFECYCLE_TRANSITION,
                    "The proposed lifecycle transition is not legal from the current status."));
        }
        return List.of();
    }

    private AgentProposalValidationResult invalid(AgentProposalError error) {
        return new AgentProposalValidationResult(null, List.of(error));
    }
}
