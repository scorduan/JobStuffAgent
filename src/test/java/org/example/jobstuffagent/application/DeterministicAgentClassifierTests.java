package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicAgentClassifierTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);
    private final DeterministicAgentClassifier classifier = new DeterministicAgentClassifier();

    @Test
    void selectsExactlyOneApplicationWhenThePromptNamesItsCompany() {
        JobApplication application = application("Example Co.", "Java Engineer");

        AgentClassification classification = classifier.classify(
                "What is the status of my Example Co. application?",
                List.of(application));

        assertEquals(AgentIntent.LOOK_UP_APPLICATION, classification.intent());
        assertEquals(List.of(application.id()), classification.selectedApplicationIds());
        assertTrue(classification.questions().isEmpty());
    }

    @Test
    void asksForClarificationWhenSeveralApplicationsMatch() {
        AgentClassification classification = classifier.classify(
                "Tell me about my Java Engineer application",
                List.of(
                        application("Example Co.", "Java Engineer"),
                        application("Other Co.", "Java Engineer")));

        assertEquals(AgentIntent.LOOK_UP_APPLICATION, classification.intent());
        assertTrue(classification.selectedApplicationIds().isEmpty());
        assertTrue(classification.needsInput());
    }

    @Test
    void listsAllApplicationIdsForAnExplicitListRequest() {
        JobApplication first = application("Example Co.", "Java Engineer");
        JobApplication second = application("Other Co.", "Platform Engineer");

        AgentClassification classification = classifier.classify(
                "List my applications",
                List.of(first, second));

        assertEquals(AgentIntent.LIST_APPLICATIONS, classification.intent());
        assertEquals(2, classification.selectedApplicationIds().size());
        assertTrue(classification.questions().isEmpty());
    }

    private JobApplication application(String company, String role) {
        return new JobApplication(UUID.randomUUID(), company, role, CLOCK);
    }
}
