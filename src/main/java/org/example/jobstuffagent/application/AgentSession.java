package org.example.jobstuffagent.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One bounded attempt to fulfill a user request within a conversation.
 */
public final class AgentSession {

    private final UUID id;
    private final UUID conversationId;
    private final String prompt;
    private final Instant startedAt;
    private final List<AgentSessionStatus> stateHistory = new ArrayList<>(List.of(AgentSessionStatus.RECEIVED));

    private AgentSessionStatus status = AgentSessionStatus.RECEIVED;
    private AgentClassification classification;
    private ApplicationLookupResult lookupResult;
    private AgentPlanningResult planningResult;
    private Instant completedAt;

    public AgentSession(UUID id, UUID conversationId, String prompt, Instant startedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        this.prompt = prompt.trim();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    }

    public UUID id() {
        return id;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public String prompt() {
        return prompt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public AgentSessionStatus status() {
        return status;
    }

    public boolean completedNeedingInput() {
        return status == AgentSessionStatus.COMPLETED_NEEDS_INPUT;
    }

    public List<AgentSessionStatus> stateHistory() {
        return List.copyOf(stateHistory);
    }

    public Optional<AgentClassification> classification() {
        return Optional.ofNullable(classification);
    }

    public Optional<ApplicationLookupResult> lookupResult() {
        return Optional.ofNullable(lookupResult);
    }

    public Optional<AgentPlanningResult> planningResult() {
        return Optional.ofNullable(planningResult);
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public void beginClassification() {
        transitionFrom(AgentSessionStatus.RECEIVED, AgentSessionStatus.CLASSIFYING);
    }

    public void completeClassification(AgentClassification classification, Instant completedAt) {
        Objects.requireNonNull(classification, "classification must not be null");
        requireStatus(AgentSessionStatus.CLASSIFYING);
        if (classification.needsInput()) {
            completeNeedingInput(AgentSessionStatus.CLASSIFYING, classification, completedAt);
            return;
        }
        this.classification = classification;
        transitionFrom(AgentSessionStatus.CLASSIFYING, AgentSessionStatus.LOOKING_UP_DATA);
    }

    public void completeLookup(ApplicationLookupResult lookupResult) {
        Objects.requireNonNull(lookupResult, "lookupResult must not be null");
        requireStatus(AgentSessionStatus.LOOKING_UP_DATA);
        this.lookupResult = lookupResult;
        this.classification = lookupResult.classification();
        transitionFrom(AgentSessionStatus.LOOKING_UP_DATA, AgentSessionStatus.PLANNING);
    }

    public void completePlanning(AgentPlanningResult planningResult, Instant completedAt) {
        Objects.requireNonNull(planningResult, "planningResult must not be null");
        requireStatus(AgentSessionStatus.PLANNING);
        this.planningResult = planningResult;
        if (planningResult.needsInput()) {
            this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
            transitionFrom(AgentSessionStatus.PLANNING, AgentSessionStatus.COMPLETED_NEEDS_INPUT);
            return;
        }
        transitionFrom(AgentSessionStatus.PLANNING, AgentSessionStatus.VALIDATING);
    }

    public void completeValidation() {
        transitionFrom(AgentSessionStatus.VALIDATING, AgentSessionStatus.EXECUTING);
    }

    public void completeExecution(ApplicationLookupResult lookupResult, Instant completedAt) {
        Objects.requireNonNull(lookupResult, "lookupResult must not be null");
        requireStatus(AgentSessionStatus.EXECUTING);
        this.lookupResult = lookupResult;
        this.classification = lookupResult.classification();
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        transitionFrom(AgentSessionStatus.EXECUTING, AgentSessionStatus.COMPLETED);
    }

    public void failValidation(ApplicationLookupResult lookupResult, Instant completedAt) {
        Objects.requireNonNull(lookupResult, "lookupResult must not be null");
        requireStatus(AgentSessionStatus.VALIDATING);
        this.lookupResult = lookupResult;
        this.classification = lookupResult.classification();
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        transitionFrom(AgentSessionStatus.VALIDATING, AgentSessionStatus.FAILED);
    }

    private void completeNeedingInput(
            AgentSessionStatus expectedStatus,
            AgentClassification classification,
            Instant completedAt) {
        requireStatus(expectedStatus);
        this.classification = classification;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        transitionFrom(expectedStatus, AgentSessionStatus.COMPLETED_NEEDS_INPUT);
    }

    private void transitionFrom(AgentSessionStatus expectedStatus, AgentSessionStatus targetStatus) {
        requireStatus(expectedStatus);
        status = targetStatus;
        stateHistory.add(targetStatus);
    }

    private void requireStatus(AgentSessionStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException(
                    "Cannot change agent session from " + status + " while expecting " + expectedStatus);
        }
    }
}
