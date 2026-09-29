package org.example.jobstuffagent.application;

import org.example.jobstuffagent.adapter.persistence.InMemoryJobApplicationRepository;
import org.example.jobstuffagent.domain.ApplicationStatus;
import org.example.jobstuffagent.domain.JobApplication;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationToolExecutorTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void executesAValidatedStatusTransitionAndRecordsTheOutcome() {
        JobApplicationService service = new JobApplicationService(
                new InMemoryJobApplicationRepository(), UUID::randomUUID, CLOCK);
        JobApplication application = service.create(new CreateJobApplicationCommand(
                "Example Co.", "Engineer", null));
        AgentProposal proposal = new AgentProposal(
                AgentAction.TRANSITION_APPLICATION_STATUS,
                application.id(),
                ApplicationStatus.APPLIED,
                LocalDate.of(2026, 9, 25),
                "agent",
                "Submitted today.");
        AgentValidatedPlan plan = new AgentValidatedPlan(List.of(proposal), "Validated one proposal.");

        List<ToolExecution> executions = new ApplicationToolExecutor(service, CLOCK).execute(plan);

        assertEquals(1, executions.size());
        assertEquals(ToolExecutionStatus.SUCCEEDED, executions.getFirst().status());
        assertEquals(ApplicationStatus.APPLIED, application.status());
    }
}
