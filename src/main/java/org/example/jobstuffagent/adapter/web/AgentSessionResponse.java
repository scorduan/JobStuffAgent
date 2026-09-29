package org.example.jobstuffagent.adapter.web;

import org.example.jobstuffagent.application.AgentClassification;
import org.example.jobstuffagent.application.AgentIntent;
import org.example.jobstuffagent.application.AgentPlanningResult;
import org.example.jobstuffagent.application.AgentSession;
import org.example.jobstuffagent.application.AgentSessionStatus;
import org.example.jobstuffagent.application.AgentProposalError;
import org.example.jobstuffagent.application.AgentProposal;
import org.example.jobstuffagent.application.AgentProposalValidationResult;
import org.example.jobstuffagent.application.ApplicationLookupError;
import org.example.jobstuffagent.application.ApplicationLookupResult;
import org.example.jobstuffagent.application.ToolExecution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentSessionResponse(
        UUID conversationId,
        UUID sessionId,
        AgentSessionStatus status,
        List<AgentSessionStatus> stateHistory,
        AgentIntent intent,
        List<UUID> selectedApplicationIds,
        List<ApplicationLookupError> lookupErrors,
        List<AgentProposal> proposedActions,
        List<AgentProposalError> validationErrors,
        List<ToolExecution> toolExecutions,
        List<String> questions,
        String summary,
        Instant startedAt,
        Instant completedAt) {

    static AgentSessionResponse from(AgentSession session) {
        AgentClassification classification = session.classification().orElseThrow();
        var lookupResult = session.lookupResult();
        return new AgentSessionResponse(
                session.conversationId(),
                session.id(),
                session.status(),
                session.stateHistory(),
                classification.intent(),
                lookupResult.map(ApplicationLookupResult::selectedApplicationIds).orElse(List.of()),
                lookupResult.map(ApplicationLookupResult::errors).orElse(List.of()),
                session.planningResult().map(AgentPlanningResult::proposals).orElse(List.of()),
                session.proposalValidationResult().map(AgentProposalValidationResult::errors).orElse(List.of()),
                session.toolExecutions(),
                session.planningResult().map(AgentPlanningResult::questions).orElse(classification.questions()),
                session.planningResult().map(AgentPlanningResult::summary)
                        .or(() -> lookupResult.map(ApplicationLookupResult::summary))
                        .orElse(classification.summary()),
                session.startedAt(),
                session.completedAt().orElse(null));
    }
}
