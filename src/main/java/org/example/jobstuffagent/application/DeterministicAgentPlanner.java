package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.ApplicationStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase-four deterministic planning stub that recognizes an explicit status transition request.
 */
@Component
public class DeterministicAgentPlanner {

    private static final Pattern MARK_AS_STATUS = Pattern.compile(
            "(?i)\\bmark\\s+my\\s+(.+?)\\s+application\\s+as\\s+([a-z_]+)\\b");

    private final Clock clock;

    public DeterministicAgentPlanner(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AgentPlanningResult plan(String prompt, ApplicationLookupResult lookupResult) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(lookupResult, "lookupResult must not be null");
        if (!lookupResult.errors().isEmpty()) {
            return new AgentPlanningResult(
                    List.of(),
                    lookupResult.errors().stream().map(ApplicationLookupError::message).toList(),
                    "More information is required to continue.");
        }

        Matcher match = MARK_AS_STATUS.matcher(prompt);
        if (!match.find()) {
            return new AgentPlanningResult(List.of(), List.of(), lookupResult.summary());
        }
        if (lookupResult.selectedApplicationIds().size() != 1) {
            return new AgentPlanningResult(
                    List.of(),
                    List.of("Which single application should I update?"),
                    "More information is required to select one application.");
        }

        ApplicationStatus targetStatus;
        try {
            targetStatus = ApplicationStatus.valueOf(match.group(2).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return new AgentPlanningResult(
                    List.of(),
                    List.of("Which supported application status should I use?"),
                    "The requested status is not supported.");
        }

        AgentProposal proposal = new AgentProposal(
                AgentAction.TRANSITION_APPLICATION_STATUS,
                lookupResult.selectedApplicationIds().getFirst(),
                targetStatus,
                LocalDate.now(clock),
                "user-confirmed-agent-request",
                "Requested by the user: " + prompt.trim());
        return new AgentPlanningResult(
                List.of(proposal),
                List.of(),
                "Proposed transition to " + targetStatus + ".");
    }
}
