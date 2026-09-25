package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates the bounded workflow and sends only validated proposals to deterministic tools.
 */
@Service
public class AgentWorkflowService {

    private final ConversationRepository conversationRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final DeterministicAgentClassifier classifier;
    private final DeterministicAgentPlanner planner;
    private final AgentProposalValidator proposalValidator;
    private final ApplicationToolExecutor toolExecutor;
    private final JobApplicationService jobApplicationService;
    private final Clock clock;

    public AgentWorkflowService(
            ConversationRepository conversationRepository,
            AgentSessionRepository agentSessionRepository,
            DeterministicAgentClassifier classifier,
            DeterministicAgentPlanner planner,
            AgentProposalValidator proposalValidator,
            ApplicationToolExecutor toolExecutor,
            JobApplicationService jobApplicationService,
            Clock clock) {
        this.conversationRepository = Objects.requireNonNull(
                conversationRepository, "conversationRepository must not be null");
        this.agentSessionRepository = Objects.requireNonNull(
                agentSessionRepository, "agentSessionRepository must not be null");
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
        this.planner = Objects.requireNonNull(planner, "planner must not be null");
        this.proposalValidator = Objects.requireNonNull(
                proposalValidator, "proposalValidator must not be null");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor must not be null");
        this.jobApplicationService = Objects.requireNonNull(
                jobApplicationService, "jobApplicationService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @PreAuthorize("hasAnyRole('APPLICATIONS_READ_ONLY', 'APPLICATIONS_MANAGER')")
    public Optional<AgentSession> start(StartWorkflowCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<Conversation> existingConversation = command.conversationId() == null
                ? Optional.empty()
                : conversationRepository.findById(command.conversationId());
        if (command.conversationId() != null && existingConversation.isEmpty()) {
            return Optional.empty();
        }

        Conversation conversation = existingConversation.orElseGet(
                () -> new Conversation(UUID.randomUUID(), clock.instant()));
        AgentSession session = createSession(conversation, command.prompt());

        AgentClassification classification = classify(session, command.prompt());
        if (session.completedNeedingInput()) {
            persist(conversation, session);
            return Optional.of(session);
        }

        ApplicationLookupResult lookupResult = lookup(session, classification);
        AgentPlanningResult planningResult = plan(session, command.prompt(), lookupResult);
        if (session.completedNeedingInput()) {
            persist(conversation, session);
            return Optional.of(session);
        }

        AgentProposalValidationResult validationResult = validate(session, planningResult, lookupResult);
        if (session.status() == AgentSessionStatus.FAILED) {
            persist(conversation, session);
            return Optional.of(session);
        }
        execute(session, validationResult);
        persist(conversation, session);

        return Optional.of(session);
    }

    private void persist(Conversation conversation, AgentSession session) {
        conversation.addSession(session.id());
        conversationRepository.save(conversation);
        agentSessionRepository.save(session);
    }

    private AgentClassification classify(AgentSession session, String prompt) {
        session.beginClassification();
        AgentClassification classification = classifier.classify(prompt);
        session.completeClassification(classification, clock.instant());
        return classification;
    }

    private ApplicationLookupResult lookup(
            AgentSession session,
            AgentClassification classification) {
        ApplicationLookupResult lookupResult = selectApplications(classification);
        session.completeLookup(lookupResult);
        return lookupResult;
    }

    private AgentPlanningResult plan(
            AgentSession session,
            String prompt,
            ApplicationLookupResult lookupResult) {
        AgentPlanningResult planningResult = planner.plan(prompt, lookupResult);
        session.completePlanning(planningResult, clock.instant());
        return planningResult;
    }

    private AgentProposalValidationResult validate(
            AgentSession session,
            AgentPlanningResult planningResult,
            ApplicationLookupResult lookupResult) {
        if (!planningResult.proposals().isEmpty()) {
            AgentProposalValidationResult validationResult = proposalValidator.validate(
                    planningResult.proposals(), lookupResult.selectedApplicationIds());
            session.completeValidation(validationResult, clock.instant());
            return validationResult;
        }

        if (!selectionIsStillAvailable(lookupResult)) {
            AgentProposalValidationResult validationResult = new AgentProposalValidationResult(
                    null,
                    List.of(new AgentProposalError(
                            AgentProposalErrorCode.APPLICATION_NOT_FOUND,
                            "Selected application data changed before validation.")));
            session.completeValidation(validationResult, clock.instant());
            return validationResult;
        }

        AgentProposalValidationResult validationResult = new AgentProposalValidationResult(
                new AgentValidatedPlan(List.of(), "No mutation was proposed."),
                List.of());
        session.completeValidation(validationResult, clock.instant());
        return validationResult;
    }

    private void execute(AgentSession session, AgentProposalValidationResult validationResult) {
        List<ToolExecution> executions = validationResult.validatedPlan() == null
                || validationResult.validatedPlan().proposals().isEmpty()
                ? List.of()
                : toolExecutor.execute(validationResult.validatedPlan());
        session.completeExecution(executions, clock.instant());
    }

    private AgentSession createSession(Conversation conversation, String prompt) {
        return new AgentSession(UUID.randomUUID(), conversation.id(), prompt, clock.instant());
    }

    private boolean selectionIsStillAvailable(ApplicationLookupResult lookupResult) {
        List<UUID> selectedIds = lookupResult.selectedApplicationIds();
        long availableCount = jobApplicationService.findAll().stream()
                .filter(application -> selectedIds.contains(application.id()))
                .count();
        return availableCount == selectedIds.size();
    }

    @PreAuthorize("hasAnyRole('APPLICATIONS_READ_ONLY', 'APPLICATIONS_MANAGER')")
    public List<AgentSession> findAllSessions() {
        return agentSessionRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('APPLICATIONS_READ_ONLY', 'APPLICATIONS_MANAGER')")
    public Optional<AgentSession> findSessionById(UUID id) {
        return agentSessionRepository.findById(id);
    }

    private ApplicationLookupResult selectApplications(AgentClassification classification) {
        List<JobApplication> applications = jobApplicationService.findAll();
        if (classification.intent() == AgentIntent.LIST_APPLICATIONS) {
            List<UUID> applicationIds = applications.stream()
                    .sorted(Comparator.comparing(JobApplication::company).thenComparing(JobApplication::role))
                    .map(JobApplication::id)
                    .toList();
            return new ApplicationLookupResult(
                    classification,
                    applicationIds,
                    List.of(),
                    "Found " + applicationIds.size() + " application(s).");
        }

        ApplicationLookupCriteria criteria = classification.lookupCriteria();
        List<JobApplication> matches = applications.stream()
                .filter(application -> matchesCriteria(application, criteria))
                .toList();
        if (matches.size() == 1) {
            JobApplication application = matches.getFirst();
            return new ApplicationLookupResult(
                    classification,
                    List.of(application.id()),
                    List.of(),
                    "Selected the " + application.role() + " application at " + application.company() + ".");
        }
        if (matches.size() > 1) {
            String options = matches.stream()
                    .map(application -> application.company() + " — " + application.role())
                    .collect(java.util.stream.Collectors.joining("; "));
            return new ApplicationLookupResult(
                    classification,
                    List.of(),
                    List.of(new ApplicationLookupError(
                            ApplicationLookupErrorCode.AMBIGUOUS_MATCH,
                            "I found multiple matching applications: " + options + ".")),
                    "Multiple applications matched the lookup criteria.");
        }
        return new ApplicationLookupResult(
                classification,
                List.of(),
                List.of(new ApplicationLookupError(
                        ApplicationLookupErrorCode.NO_MATCH,
                        "No application matched the lookup criteria.")),
                "No application matched the lookup criteria.");
    }

    private boolean matchesCriteria(JobApplication application, ApplicationLookupCriteria criteria) {
        return (criteria.company() != null
                && normalizeLookupValue(application.company()).equals(normalizeLookupValue(criteria.company())))
                || (criteria.role() != null
                && normalizeLookupValue(application.role()).equals(normalizeLookupValue(criteria.role())));
    }

    private String normalizeLookupValue(String value) {
        return value.replaceAll("[.!?,]+$", "").trim().toLowerCase(java.util.Locale.ROOT);
    }
}
