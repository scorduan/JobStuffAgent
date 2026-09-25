package org.example.jobstuffagent.application;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * A deliberately small classifier used before any model integration exists.
 */
@Component
public class DeterministicAgentClassifier {

    public AgentClassification classify(String prompt) {
        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT);

        if (asksToListApplications(normalizedPrompt)) {
            return new AgentClassification(
                    AgentIntent.LIST_APPLICATIONS,
                    ApplicationLookupCriteria.none(),
                    java.util.List.of(),
                    "The request is to list applications.");
        }

        if (normalizedPrompt.contains("application")) {
            return new AgentClassification(
                    AgentIntent.LOOK_UP_APPLICATION,
                    criteriaFromPrompt(prompt),
                    java.util.List.of(),
                    "The request is to look up one application.");
        }

        return new AgentClassification(
                AgentIntent.UNKNOWN,
                ApplicationLookupCriteria.none(),
                java.util.List.of("Which application do you mean? Please include the company or role."),
                "More information is required to identify an application.");
    }

    private boolean asksToListApplications(String prompt) {
        return prompt.contains("list") || prompt.contains("show") || prompt.contains("what applications");
    }

    private ApplicationLookupCriteria criteriaFromPrompt(String prompt) {
        String reference = prompt.replaceFirst("(?i).*?\\bmy\\s+", "")
                .replaceFirst("(?i)\\s+application\\b.*$", "")
                .replaceAll("[?!.,]+$", "")
                .trim();
        if (reference.isBlank()) {
            return ApplicationLookupCriteria.none();
        }
        if (reference.toLowerCase(Locale.ROOT).contains(" co")) {
            return new ApplicationLookupCriteria(reference, null);
        }
        return new ApplicationLookupCriteria(null, reference);
    }

}
