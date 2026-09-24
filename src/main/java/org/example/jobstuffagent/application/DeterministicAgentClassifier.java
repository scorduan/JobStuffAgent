package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A deliberately small classifier used before any model integration exists.
 */
@Component
public class DeterministicAgentClassifier {

    public AgentClassification classify(String prompt, List<JobApplication> applications) {
        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT);

        if (asksToListApplications(normalizedPrompt)) {
            List<UUID> applicationIds = applications.stream()
                    .sorted(Comparator.comparing(JobApplication::company).thenComparing(JobApplication::role))
                    .map(JobApplication::id)
                    .toList();
            return new AgentClassification(
                    AgentIntent.LIST_APPLICATIONS,
                    applicationIds,
                    List.of(),
                    "Found " + applicationIds.size() + " application(s).");
        }

        List<JobApplication> matches = applications.stream()
                .filter(application -> refersTo(application, normalizedPrompt))
                .toList();

        if (matches.size() == 1) {
            JobApplication application = matches.getFirst();
            return new AgentClassification(
                    AgentIntent.LOOK_UP_APPLICATION,
                    List.of(application.id()),
                    List.of(),
                    "Selected the " + application.role() + " application at " + application.company() + ".");
        }

        if (matches.size() > 1) {
            String options = matches.stream()
                    .map(application -> application.company() + " — " + application.role())
                    .collect(java.util.stream.Collectors.joining("; "));
            return new AgentClassification(
                    AgentIntent.LOOK_UP_APPLICATION,
                    List.of(),
                    List.of("I found multiple matching applications: " + options + ". Which one do you mean?"),
                    "More information is required to select one application.");
        }

        return new AgentClassification(
                AgentIntent.UNKNOWN,
                List.of(),
                List.of("Which application do you mean? Please include the company or role."),
                "More information is required to identify an application.");
    }

    private boolean asksToListApplications(String prompt) {
        return prompt.contains("list") || prompt.contains("show") || prompt.contains("what applications");
    }

    private boolean refersTo(JobApplication application, String normalizedPrompt) {
        return normalizedPrompt.contains(application.company().toLowerCase(Locale.ROOT))
                || normalizedPrompt.contains(application.role().toLowerCase(Locale.ROOT));
    }
}
