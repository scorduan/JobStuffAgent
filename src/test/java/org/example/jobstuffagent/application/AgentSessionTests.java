package org.example.jobstuffagent.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSessionTests {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void rejectsPlanningCompletionBeforePlanningAndDoesNotMutateTheSession() {
        AgentSession session = newSession();

        assertThrows(IllegalStateException.class, () -> session.completePlanning(
                new AgentPlanningResult(List.of(), List.of(), "No action is required."), NOW));

        assertEquals(AgentSessionStatus.RECEIVED, session.status());
        assertTrue(session.planningResult().isEmpty());
        assertEquals(List.of(AgentSessionStatus.RECEIVED), session.stateHistory());
    }

    @Test
    void recordsClassificationQuestionsAsATerminalSessionOutcome() {
        AgentSession session = newSession();
        session.beginClassification();

        session.completeClassification(new AgentClassification(
                AgentIntent.UNKNOWN,
                ApplicationLookupCriteria.none(),
                List.of("Which application do you mean?"),
                "More information is required."), NOW);

        assertTrue(session.completedNeedingInput());
        assertTrue(session.classification().orElseThrow().needsInput());
        assertEquals(List.of(
                AgentSessionStatus.RECEIVED,
                AgentSessionStatus.CLASSIFYING,
                AgentSessionStatus.COMPLETED_NEEDS_INPUT), session.stateHistory());
    }

    @ParameterizedTest(name = "{0} permits {1}")
    @MethodSource("stateAndOperations")
    void allowsOnlyTheTransitionsDefinedForEachState(
            AgentSessionStatus startingStatus,
            Operation operation) {
        AgentSession session = sessionAt(startingStatus);
        AgentSessionStatus statusBefore = session.status();
        List<AgentSessionStatus> historyBefore = session.stateHistory();
        AgentClassification classificationBefore = session.classification().orElse(null);
        ApplicationLookupResult lookupResultBefore = session.lookupResult().orElse(null);
        AgentPlanningResult planningResultBefore = session.planningResult().orElse(null);
        Instant completedAtBefore = session.completedAt().orElse(null);

        if (operation.allowedFrom(startingStatus)) {
            assertDoesNotThrow(() -> operation.apply(session));
            assertEquals(operation.targetStatus(), session.status());
            return;
        }

        assertThrows(IllegalStateException.class, () -> operation.apply(session));
        assertEquals(statusBefore, session.status());
        assertEquals(historyBefore, session.stateHistory());
        assertEquals(classificationBefore, session.classification().orElse(null));
        assertEquals(lookupResultBefore, session.lookupResult().orElse(null));
        assertEquals(planningResultBefore, session.planningResult().orElse(null));
        assertEquals(completedAtBefore, session.completedAt().orElse(null));
    }

    private static Stream<Arguments> stateAndOperations() {
        return Stream.of(AgentSessionStatus.values())
                .flatMap(status -> Stream.of(Operation.values())
                        .map(operation -> Arguments.of(status, operation)));
    }

    private AgentSession sessionAt(AgentSessionStatus targetStatus) {
        AgentSession session = newSession();
        if (targetStatus == AgentSessionStatus.RECEIVED) {
            return session;
        }

        session.beginClassification();
        if (targetStatus == AgentSessionStatus.CLASSIFYING) {
            return session;
        }

        if (targetStatus == AgentSessionStatus.COMPLETED_NEEDS_INPUT) {
            session.completeClassification(classificationNeedingInput(), NOW);
            return session;
        }

        session.completeClassification(classification(), NOW);
        if (targetStatus == AgentSessionStatus.LOOKING_UP_DATA) {
            return session;
        }

        session.completeLookup(lookupResult());
        if (targetStatus == AgentSessionStatus.PLANNING) {
            return session;
        }

        session.completePlanning(new AgentPlanningResult(List.of(), List.of(), "Ready to continue."), NOW);
        if (targetStatus == AgentSessionStatus.VALIDATING) {
            return session;
        }

        if (targetStatus == AgentSessionStatus.FAILED) {
            session.completeValidation(new AgentProposalValidationResult(
                    null,
                    List.of(new AgentProposalError(
                            AgentProposalErrorCode.INVALID_LIFECYCLE_TRANSITION,
                            "Not legal."))), NOW);
            return session;
        }

        session.completeValidation(validValidationResult(), NOW);
        if (targetStatus == AgentSessionStatus.EXECUTING) {
            return session;
        }

        session.completeExecution(List.of(), NOW);
        return session;
    }

    private static AgentClassification classification() {
        return new AgentClassification(
                AgentIntent.LOOK_UP_APPLICATION,
                new ApplicationLookupCriteria("Example Co", null),
                List.of(),
                "Ready to look up the application.");
    }

    private static AgentClassification classificationNeedingInput() {
        return new AgentClassification(
                AgentIntent.UNKNOWN,
                ApplicationLookupCriteria.none(),
                List.of("Which application do you mean?"),
                "More information is required.");
    }

    private static ApplicationLookupResult lookupResult() {
        return new ApplicationLookupResult(classification(), List.of(UUID.randomUUID()), List.of(), "Found one.");
    }

    private static AgentProposalValidationResult validValidationResult() {
        return new AgentProposalValidationResult(
                new AgentValidatedPlan(List.of(), "No mutation was proposed."),
                List.of());
    }

    private enum Operation {
        BEGIN_CLASSIFICATION {
            @Override
            void apply(AgentSession session) {
                session.beginClassification();
            }

            @Override
            AgentSessionStatus targetStatus() {
                return AgentSessionStatus.CLASSIFYING;
            }
        },
        COMPLETE_CLASSIFICATION {
            @Override
            void apply(AgentSession session) {
                session.completeClassification(classification(), NOW);
            }

            @Override
            AgentSessionStatus targetStatus() {
                return AgentSessionStatus.LOOKING_UP_DATA;
            }
        },
        COMPLETE_LOOKUP {
            @Override
            void apply(AgentSession session) {
                session.completeLookup(lookupResult());
            }

            @Override
            AgentSessionStatus targetStatus() {
                return AgentSessionStatus.PLANNING;
            }
        },
        COMPLETE_PLANNING {
            @Override
            void apply(AgentSession session) {
                session.completePlanning(new AgentPlanningResult(List.of(), List.of(), "Ready."), NOW);
            }

            @Override
            AgentSessionStatus targetStatus() {
                return AgentSessionStatus.VALIDATING;
            }
        },
        COMPLETE_VALIDATION {
            @Override
            void apply(AgentSession session) {
                session.completeValidation(validValidationResult(), NOW);
            }

            @Override
            AgentSessionStatus targetStatus() {
                return AgentSessionStatus.EXECUTING;
            }
        },
        REJECT_VALIDATION {
            @Override
            void apply(AgentSession session) {
                session.completeValidation(new AgentProposalValidationResult(
                        null,
                        List.of(new AgentProposalError(
                                AgentProposalErrorCode.INVALID_LIFECYCLE_TRANSITION,
                                "Not legal."))), NOW);
            }

            @Override
            AgentSessionStatus targetStatus() {
                return AgentSessionStatus.FAILED;
            }
        },
        COMPLETE_EXECUTION {
            @Override
            void apply(AgentSession session) {
                session.completeExecution(List.of(), NOW);
            }

            @Override
            AgentSessionStatus targetStatus() {
                return AgentSessionStatus.COMPLETED;
            }
        };

        abstract void apply(AgentSession session);

        abstract AgentSessionStatus targetStatus();

        boolean allowedFrom(AgentSessionStatus status) {
            return switch (this) {
                case BEGIN_CLASSIFICATION -> status == AgentSessionStatus.RECEIVED;
                case COMPLETE_CLASSIFICATION -> status == AgentSessionStatus.CLASSIFYING;
                case COMPLETE_LOOKUP -> status == AgentSessionStatus.LOOKING_UP_DATA;
                case COMPLETE_PLANNING -> status == AgentSessionStatus.PLANNING;
                case COMPLETE_VALIDATION, REJECT_VALIDATION -> status == AgentSessionStatus.VALIDATING;
                case COMPLETE_EXECUTION -> status == AgentSessionStatus.EXECUTING;
            };
        }
    }

    private static AgentSession newSession() {
        return new AgentSession(UUID.randomUUID(), UUID.randomUUID(), "Test prompt", NOW);
    }
}
