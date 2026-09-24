package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates the bounded phase-three workflow without model calls or mutation tools.
 */
@Service
public class AgentWorkflowService {

    private final ConversationRepository conversationRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final DeterministicAgentClassifier classifier;
    private final JobApplicationService jobApplicationService;
    private final Clock clock;

    public AgentWorkflowService(
            ConversationRepository conversationRepository,
            AgentSessionRepository agentSessionRepository,
            DeterministicAgentClassifier classifier,
            JobApplicationService jobApplicationService,
            Clock clock) {
        this.conversationRepository = Objects.requireNonNull(
                conversationRepository, "conversationRepository must not be null");
        this.agentSessionRepository = Objects.requireNonNull(
                agentSessionRepository, "agentSessionRepository must not be null");
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
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
        AgentSession session = new AgentSession(
                UUID.randomUUID(),
                conversation.id(),
                command.prompt(),
                clock.instant());

        conversation.addSession(session.id());
        conversationRepository.save(conversation);
        agentSessionRepository.save(session);

        session.beginClassification();
        List<JobApplication> applications = jobApplicationService.findAll();
        session.complete(classifier.classify(command.prompt(), applications), clock.instant());
        agentSessionRepository.save(session);

        return Optional.of(session);
    }

    @PreAuthorize("hasAnyRole('APPLICATIONS_READ_ONLY', 'APPLICATIONS_MANAGER')")
    public List<AgentSession> findAllSessions() {
        return agentSessionRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('APPLICATIONS_READ_ONLY', 'APPLICATIONS_MANAGER')")
    public Optional<AgentSession> findSessionById(UUID id) {
        return agentSessionRepository.findById(id);
    }
}
