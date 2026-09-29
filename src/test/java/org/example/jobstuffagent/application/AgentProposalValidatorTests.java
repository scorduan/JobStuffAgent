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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProposalValidatorTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptsASelectedLegalTransition() {
        JobApplicationService service = service();
        JobApplication application = service.create(new CreateJobApplicationCommand(
                "Example Co.", "Engineer", null));
        AgentProposal proposal = proposal(application.id(), ApplicationStatus.APPLIED);

        AgentProposalValidationResult result = new AgentProposalValidator(service).validate(
                List.of(proposal), List.of(application.id()));

        assertTrue(result.valid());
        assertEquals(List.of(proposal), result.validatedPlan().proposals());
    }

    @Test
    void rejectsAnIllegalTransitionWithoutChangingTheApplication() {
        JobApplicationService service = service();
        JobApplication application = service.create(new CreateJobApplicationCommand(
                "Example Co.", "Engineer", null));
        AgentProposal proposal = proposal(application.id(), ApplicationStatus.ACCEPTED);

        AgentProposalValidationResult result = new AgentProposalValidator(service).validate(
                List.of(proposal), List.of(application.id()));

        assertFalse(result.valid());
        assertEquals(AgentProposalErrorCode.INVALID_LIFECYCLE_TRANSITION,
                result.errors().getFirst().code());
        assertEquals(ApplicationStatus.RECOMMENDED, application.status());
        assertTrue(application.transitionHistory().isEmpty());
    }

    @Test
    void rejectsAProposalOutsideTheSelectedRecords() {
        JobApplicationService service = service();
        JobApplication application = service.create(new CreateJobApplicationCommand(
                "Example Co.", "Engineer", null));

        AgentProposalValidationResult result = new AgentProposalValidator(service).validate(
                List.of(proposal(application.id(), ApplicationStatus.APPLIED)), List.of());

        assertFalse(result.valid());
        assertEquals(AgentProposalErrorCode.APPLICATION_NOT_SELECTED,
                result.errors().getFirst().code());
    }

    @Test
    void rejectsMultipleMutationProposalsUntilAnExecutionPolicyExists() {
        JobApplicationService service = service();
        JobApplication application = service.create(new CreateJobApplicationCommand(
                "Example Co.", "Engineer", null));
        AgentProposal proposal = proposal(application.id(), ApplicationStatus.APPLIED);

        AgentProposalValidationResult result = new AgentProposalValidator(service).validate(
                List.of(proposal, proposal), List.of(application.id()));

        assertFalse(result.valid());
        assertEquals(AgentProposalErrorCode.MULTIPLE_PROPOSALS_NOT_SUPPORTED,
                result.errors().getFirst().code());
    }

    private AgentProposal proposal(UUID applicationId, ApplicationStatus targetStatus) {
        return new AgentProposal(
                AgentAction.TRANSITION_APPLICATION_STATUS,
                applicationId,
                targetStatus,
                LocalDate.of(2026, 9, 25),
                "agent",
                "User-confirmed workflow action.");
    }

    private JobApplicationService service() {
        return new JobApplicationService(new InMemoryJobApplicationRepository(), UUID::randomUUID, CLOCK);
    }
}
