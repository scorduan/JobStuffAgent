package org.example.jobstuffagent.adapter.web;

import org.example.jobstuffagent.application.AgentClassification;
import org.example.jobstuffagent.application.AgentIntent;
import org.example.jobstuffagent.application.AgentSession;
import org.example.jobstuffagent.application.AgentSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentSessionResponse(
        UUID conversationId,
        UUID sessionId,
        AgentSessionStatus status,
        AgentIntent intent,
        List<UUID> selectedApplicationIds,
        List<String> questions,
        String summary,
        Instant startedAt,
        Instant completedAt) {

    static AgentSessionResponse from(AgentSession session) {
        AgentClassification classification = session.classification().orElseThrow();
        return new AgentSessionResponse(
                session.conversationId(),
                session.id(),
                session.status(),
                classification.intent(),
                classification.selectedApplicationIds(),
                classification.questions(),
                classification.summary(),
                session.startedAt(),
                session.completedAt().orElse(null));
    }
}
