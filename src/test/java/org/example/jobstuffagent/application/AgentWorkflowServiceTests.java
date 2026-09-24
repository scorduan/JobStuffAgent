package org.example.jobstuffagent.application;

import org.example.jobstuffagent.adapter.persistence.InMemoryAgentSessionRepository;
import org.example.jobstuffagent.adapter.persistence.InMemoryConversationRepository;
import org.example.jobstuffagent.adapter.persistence.InMemoryJobApplicationRepository;
import org.example.jobstuffagent.domain.JobApplication;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkflowServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void createsAConversationAndSelectsOneMatchingApplication() {
        Fixture fixture = fixture();
        JobApplication application = fixture.jobApplicationService.create(new CreateJobApplicationCommand(
                "Example Co.", "Java Engineer", null));

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "What is the status of my Example Co. application?"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.COMPLETED, session.status());
        assertEquals(AgentIntent.LOOK_UP_APPLICATION, session.classification().orElseThrow().intent());
        assertEquals(List.of(application.id()), session.classification().orElseThrow().selectedApplicationIds());
        assertTrue(fixture.conversationRepository.findById(session.conversationId()).orElseThrow()
                .sessionIds().contains(session.id()));
    }

    @Test
    void endsTheSessionWithQuestionsWhenClassificationIsAmbiguous() {
        Fixture fixture = fixture();
        fixture.jobApplicationService.create(new CreateJobApplicationCommand("Example Co.", "Java Engineer", null));
        fixture.jobApplicationService.create(new CreateJobApplicationCommand("Other Co.", "Platform Engineer", null));

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "Tell me about my Engineer application"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.COMPLETED_NEEDS_INPUT, session.status());
        assertTrue(session.classification().orElseThrow().needsInput());
    }

    @Test
    void continuesAnExistingConversationWithANewSession() {
        Fixture fixture = fixture();

        AgentSession first = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "Which application should I review?"))
                .orElseThrow();
        AgentSession second = fixture.workflowService.start(new StartWorkflowCommand(
                first.conversationId(),
                "List my applications"))
                .orElseThrow();

        assertEquals(first.conversationId(), second.conversationId());
        assertEquals(2, fixture.conversationRepository.findById(first.conversationId()).orElseThrow()
                .sessionIds().size());
    }

    @Test
    void returnsEmptyWhenAnExistingConversationDoesNotExist() {
        Fixture fixture = fixture();

        assertTrue(fixture.workflowService.start(new StartWorkflowCommand(
                UUID.randomUUID(),
                "List my applications")).isEmpty());
    }

    private Fixture fixture() {
        InMemoryJobApplicationRepository jobApplicationRepository = new InMemoryJobApplicationRepository();
        JobApplicationService jobApplicationService = new JobApplicationService(
                jobApplicationRepository,
                UUID::randomUUID,
                CLOCK);
        InMemoryConversationRepository conversationRepository = new InMemoryConversationRepository();
        AgentWorkflowService workflowService = new AgentWorkflowService(
                conversationRepository,
                new InMemoryAgentSessionRepository(),
                new DeterministicAgentClassifier(),
                jobApplicationService,
                CLOCK);
        return new Fixture(jobApplicationService, conversationRepository, workflowService);
    }

    private record Fixture(
            JobApplicationService jobApplicationService,
            InMemoryConversationRepository conversationRepository,
            AgentWorkflowService workflowService) {
    }
}
