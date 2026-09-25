package org.example.jobstuffagent.application;

import org.example.jobstuffagent.adapter.persistence.InMemoryAgentSessionRepository;
import org.example.jobstuffagent.adapter.persistence.InMemoryConversationRepository;
import org.example.jobstuffagent.adapter.persistence.InMemoryJobApplicationRepository;
import org.example.jobstuffagent.domain.JobApplication;
import org.example.jobstuffagent.domain.ApplicationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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
        assertEquals(List.of(
                AgentSessionStatus.RECEIVED,
                AgentSessionStatus.CLASSIFYING,
                AgentSessionStatus.LOOKING_UP_DATA,
                AgentSessionStatus.PLANNING,
                AgentSessionStatus.VALIDATING,
                AgentSessionStatus.EXECUTING,
                AgentSessionStatus.COMPLETED), session.stateHistory());
        assertEquals(AgentIntent.LOOK_UP_APPLICATION, session.classification().orElseThrow().intent());
        assertEquals(List.of(application.id()), session.lookupResult().orElseThrow().selectedApplicationIds());
        assertTrue(fixture.conversationRepository.findById(session.conversationId()).orElseThrow()
                .sessionIds().contains(session.id()));
    }

    @Test
    void endsTheSessionWithQuestionsWhenLookupIsAmbiguous() {
        Fixture fixture = fixture();
        fixture.jobApplicationService.create(new CreateJobApplicationCommand("Example Co.", "Java Engineer", null));
        fixture.jobApplicationService.create(new CreateJobApplicationCommand("Other Co.", "Platform Engineer", null));

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "Tell me about my Engineer application"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.COMPLETED_NEEDS_INPUT, session.status());
        assertEquals(List.of(
                AgentSessionStatus.RECEIVED,
                AgentSessionStatus.CLASSIFYING,
                AgentSessionStatus.LOOKING_UP_DATA,
                AgentSessionStatus.PLANNING,
                AgentSessionStatus.COMPLETED_NEEDS_INPUT), session.stateHistory());
        assertTrue(session.planningResult().orElseThrow().needsInput());
    }

    @Test
    void endsTheSessionWithQuestionsWhenClassificationNeedsInput() {
        Fixture fixture = seededFixture();

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "What should I do next?"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.COMPLETED_NEEDS_INPUT, session.status());
        assertEquals(List.of(
                AgentSessionStatus.RECEIVED,
                AgentSessionStatus.CLASSIFYING,
                AgentSessionStatus.COMPLETED_NEEDS_INPUT), session.stateHistory());
        assertTrue(session.classification().orElseThrow().needsInput());
        assertTrue(session.lookupResult().isEmpty());
        assertTrue(session.planningResult().isEmpty());
    }

    @Test
    void planningTurnsLookupErrorsIntoQuestions() {
        Fixture fixture = seededFixture();

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "What is the status of my Missing Co. application?"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.COMPLETED_NEEDS_INPUT, session.status());
        assertEquals(List.of(
                AgentSessionStatus.RECEIVED,
                AgentSessionStatus.CLASSIFYING,
                AgentSessionStatus.LOOKING_UP_DATA,
                AgentSessionStatus.PLANNING,
                AgentSessionStatus.COMPLETED_NEEDS_INPUT), session.stateHistory());
        assertEquals(ApplicationLookupErrorCode.NO_MATCH,
                session.lookupResult().orElseThrow().errors().getFirst().code());
        assertTrue(session.planningResult().orElseThrow().needsInput());
    }

    @Test
    void failsValidationWhenSelectedDataDisappearsBeforeValidation() {
        InMemoryJobApplicationRepository repository = new InMemoryJobApplicationRepository();
        JobApplicationService baseService = new JobApplicationService(repository, UUID::randomUUID, CLOCK);
        JobApplication application = baseService.create(new CreateJobApplicationCommand(
                "Example Co.", "Java Engineer", null));
        JobApplicationService disappearingService = new JobApplicationService(repository, UUID::randomUUID, CLOCK) {
            private int lookupCount;

            @Override
            public List<JobApplication> findAll() {
                lookupCount++;
                return lookupCount == 2 ? List.of() : super.findAll();
            }
        };
        AgentWorkflowService workflowService = workflowService(
                disappearingService,
                new InMemoryConversationRepository(),
                new InMemoryAgentSessionRepository());

        AgentSession session = workflowService.start(new StartWorkflowCommand(
                null,
                "What is the status of my Example Co. application?"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.FAILED, session.status());
        assertEquals(List.of(
                AgentSessionStatus.RECEIVED,
                AgentSessionStatus.CLASSIFYING,
                AgentSessionStatus.LOOKING_UP_DATA,
                AgentSessionStatus.PLANNING,
                AgentSessionStatus.VALIDATING,
                AgentSessionStatus.FAILED), session.stateHistory());
        assertTrue(session.completedAt().isPresent());
        assertEquals(application.id(), session.lookupResult().orElseThrow().selectedApplicationIds().getFirst());
    }

    @Test
    void plansValidatesAndExecutesAnExplicitStatusTransitionProposal() {
        Fixture fixture = fixture();
        JobApplication application = fixture.jobApplicationService.create(new CreateJobApplicationCommand(
                "Example Co.", "Java Engineer", null));

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "Mark my Example Co. application as APPLIED"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.COMPLETED, session.status());
        assertEquals(ApplicationStatus.APPLIED, application.status());
        assertEquals(1, session.planningResult().orElseThrow().proposals().size());
        assertTrue(session.proposalValidationResult().orElseThrow().valid());
        assertEquals(ToolExecutionStatus.SUCCEEDED, session.toolExecutions().getFirst().status());
        assertEquals(List.of(
                AgentSessionStatus.RECEIVED,
                AgentSessionStatus.CLASSIFYING,
                AgentSessionStatus.LOOKING_UP_DATA,
                AgentSessionStatus.PLANNING,
                AgentSessionStatus.VALIDATING,
                AgentSessionStatus.EXECUTING,
                AgentSessionStatus.COMPLETED), session.stateHistory());
    }

    @Test
    void stopsWhenAPlannedProposalFailsLifecycleValidation() {
        Fixture fixture = fixture();
        JobApplication application = fixture.jobApplicationService.create(new CreateJobApplicationCommand(
                "Example Co.", "Java Engineer", null));

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(
                null,
                "Mark my Example Co. application as ACCEPTED"))
                .orElseThrow();

        assertEquals(AgentSessionStatus.FAILED, session.status());
        assertEquals(ApplicationStatus.RECOMMENDED, application.status());
        assertEquals(AgentProposalErrorCode.INVALID_LIFECYCLE_TRANSITION,
                session.proposalValidationResult().orElseThrow().errors().getFirst().code());
        assertTrue(session.toolExecutions().isEmpty());
        assertEquals(AgentSessionStatus.FAILED, session.stateHistory().getLast());
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("workflowPrompts")
    void exercisesThePhaseThreePromptPaths(
            String prompt,
            AgentSessionStatus expectedStatus,
            int expectedSelectionCount,
            boolean expectedPlanningQuestions) {
        Fixture fixture = seededFixture();

        AgentSession session = fixture.workflowService.start(new StartWorkflowCommand(null, prompt))
                .orElseThrow();

        assertEquals(expectedStatus, session.status());
        assertEquals(expectedSelectionCount,
                session.lookupResult().map(result -> result.selectedApplicationIds().size()).orElse(0));
        assertEquals(expectedPlanningQuestions,
                session.planningResult().map(AgentPlanningResult::needsInput).orElse(false));
    }

    private static Stream<Arguments> workflowPrompts() {
        return Stream.of(
                Arguments.of("List my applications", AgentSessionStatus.COMPLETED, 3, false),
                Arguments.of("Show all applications", AgentSessionStatus.COMPLETED, 3, false),
                Arguments.of("What applications do I have?", AgentSessionStatus.COMPLETED, 3, false),
                Arguments.of("What is the status of my Example Co. application?",
                        AgentSessionStatus.COMPLETED, 1, false),
                Arguments.of("Tell me about my Java Engineer application",
                        AgentSessionStatus.COMPLETED, 1, false),
                Arguments.of("Tell me about my Engineer application",
                        AgentSessionStatus.COMPLETED_NEEDS_INPUT, 0, true),
                Arguments.of("What is the status of my Missing Co. application?",
                        AgentSessionStatus.COMPLETED_NEEDS_INPUT, 0, true),
                Arguments.of("What is the status of my application?",
                        AgentSessionStatus.COMPLETED_NEEDS_INPUT, 0, true),
                Arguments.of("What should I do next?",
                        AgentSessionStatus.COMPLETED_NEEDS_INPUT, 0, false));
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
                new DeterministicAgentPlanner(CLOCK),
                new AgentProposalValidator(jobApplicationService),
                new ApplicationToolExecutor(jobApplicationService, CLOCK),
                jobApplicationService,
                CLOCK);
        return new Fixture(jobApplicationService, conversationRepository, workflowService);
    }

    private AgentWorkflowService workflowService(
            JobApplicationService jobApplicationService,
            InMemoryConversationRepository conversationRepository,
            InMemoryAgentSessionRepository agentSessionRepository) {
        return new AgentWorkflowService(
                conversationRepository,
                agentSessionRepository,
                new DeterministicAgentClassifier(),
                new DeterministicAgentPlanner(CLOCK),
                new AgentProposalValidator(jobApplicationService),
                new ApplicationToolExecutor(jobApplicationService, CLOCK),
                jobApplicationService,
                CLOCK);
    }

    private Fixture seededFixture() {
        Fixture fixture = fixture();
        fixture.jobApplicationService.create(new CreateJobApplicationCommand(
                "Example Co.", "Java Engineer", null));
        fixture.jobApplicationService.create(new CreateJobApplicationCommand(
                "Other Co.", "Platform Engineer", null));
        fixture.jobApplicationService.create(new CreateJobApplicationCommand(
                "Acme Labs", "Product Manager", null));
        return fixture;
    }

    private record Fixture(
            JobApplicationService jobApplicationService,
            InMemoryConversationRepository conversationRepository,
            AgentWorkflowService workflowService) {
    }
}
