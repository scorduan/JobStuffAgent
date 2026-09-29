package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes the validated application tools. Mutation remains manager-authorized.
 */
@Service
public class ApplicationToolExecutor {

    private final JobApplicationService jobApplicationService;
    private final Clock clock;

    public ApplicationToolExecutor(JobApplicationService jobApplicationService, Clock clock) {
        this.jobApplicationService = Objects.requireNonNull(
                jobApplicationService, "jobApplicationService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @PreAuthorize("hasRole('APPLICATIONS_MANAGER')")
    public List<ToolExecution> execute(AgentValidatedPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        List<ToolExecution> executions = new ArrayList<>();
        for (AgentProposal proposal : plan.proposals()) {
            executions.add(executeProposal(proposal));
        }
        return List.copyOf(executions);
    }

    private ToolExecution executeProposal(AgentProposal proposal) {
        Instant executedAt = clock.instant();
        try {
            JobApplication application = jobApplicationService.transitionStatus(
                    proposal.applicationId(),
                    proposal.targetStatus(),
                    proposal.effectiveDate(),
                    proposal.source(),
                    proposal.note());
            return new ToolExecution(
                    proposal,
                    ToolExecutionStatus.SUCCEEDED,
                    executedAt,
                    "Transitioned application " + application.id() + " to " + application.status() + ".");
        } catch (IllegalStateException exception) {
            return new ToolExecution(
                    proposal,
                    ToolExecutionStatus.FAILED,
                    executedAt,
                    exception.getMessage() == null
                            ? "The application transition failed."
                            : exception.getMessage());
        }
    }
}
