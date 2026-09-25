package org.example.jobstuffagent.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicAgentClassifierTests {

    private final DeterministicAgentClassifier classifier = new DeterministicAgentClassifier();

    @Test
    void selectsExactlyOneApplicationWhenThePromptNamesItsCompany() {
        AgentClassification classification = classifier.classify(
                "What is the status of my Example Co. application?");

        assertEquals(AgentIntent.LOOK_UP_APPLICATION, classification.intent());
        assertEquals("Example Co", classification.lookupCriteria().company());
        assertTrue(classification.lookupCriteria().role() == null);
        assertTrue(classification.questions().isEmpty());
    }

    @Test
    void asksForClarificationWhenSeveralApplicationsMatch() {
        AgentClassification classification = classifier.classify(
                "Tell me about my Java Engineer application");

        assertEquals(AgentIntent.LOOK_UP_APPLICATION, classification.intent());
        assertEquals("Java Engineer", classification.lookupCriteria().role());
        assertTrue(classification.questions().isEmpty());
    }

    @Test
    void listsAllApplicationIdsForAnExplicitListRequest() {
        AgentClassification classification = classifier.classify(
                "List my applications");

        assertEquals(AgentIntent.LIST_APPLICATIONS, classification.intent());
        assertTrue(!classification.lookupCriteria().hasCriteria());
        assertTrue(classification.questions().isEmpty());
    }
}
