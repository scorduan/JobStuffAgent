package org.example.jobstuffagent.domain;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobApplicationTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-23T12:00:00Z"),
            ZoneOffset.UTC);
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 9, 23);
    private static final String USER = "user";
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ApplicationStatus.RECOMMENDED, Set.of(ApplicationStatus.APPLIED),
            ApplicationStatus.APPLIED, Set.of(
                    ApplicationStatus.INTERVIEWING,
                    ApplicationStatus.STALE,
                    ApplicationStatus.REJECTED),
            ApplicationStatus.INTERVIEWING, Set.of(
                    ApplicationStatus.OFFERED,
                    ApplicationStatus.REJECTED),
            ApplicationStatus.OFFERED, Set.of(
                    ApplicationStatus.ACCEPTED,
                    ApplicationStatus.DECLINED,
                    ApplicationStatus.REJECTED),
            ApplicationStatus.STALE, Set.of(
                    ApplicationStatus.PRESUMED_DEAD,
                    ApplicationStatus.INTERVIEWING,
                    ApplicationStatus.REJECTED),
            ApplicationStatus.PRESUMED_DEAD, Set.of(
                    ApplicationStatus.INTERVIEWING,
                    ApplicationStatus.REJECTED),
            ApplicationStatus.ACCEPTED, Set.of(),
            ApplicationStatus.DECLINED, Set.of(),
            ApplicationStatus.REJECTED, Set.of());

    @Test
    void startsRecommendedWithItsSourceUrl() {
        URI sourceUrl = URI.create("https://careers.example.com/jobs/123");

        JobApplication application = new JobApplication(
                UUID.randomUUID(), "Example Co.", "Java Engineer", sourceUrl, CLOCK);

        assertEquals(ApplicationStatus.RECOMMENDED, application.status());
        assertEquals(sourceUrl, application.sourceUrl().orElseThrow());
        assertTrue(application.transitionHistory().isEmpty());
    }

    @Test
    void recordsThePrimaryLifecycleAsAnAuditHistory() {
        JobApplication application = newApplication();

        application.markApplied(EFFECTIVE_DATE, USER, "Submitted through the careers site");
        application.beginInterviewing(EFFECTIVE_DATE.plusDays(3), USER, "Recruiter screen scheduled");
        application.recordOffer(EFFECTIVE_DATE.plusDays(10), USER, "Written offer received");
        application.accept(EFFECTIVE_DATE.plusDays(12), USER, "Offer accepted");

        assertEquals(ApplicationStatus.ACCEPTED, application.status());
        assertEquals(4, application.transitionHistory().size());

        ApplicationTransition applied = application.transitionHistory().getFirst();
        assertEquals(ApplicationStatus.RECOMMENDED, applied.fromStatus());
        assertEquals(ApplicationStatus.APPLIED, applied.toStatus());
        assertEquals(EFFECTIVE_DATE, applied.effectiveDate());
        assertEquals(CLOCK.instant(), applied.recordedAt());
        assertEquals(USER, applied.source());
    }

    @Test
    void canReviveAStaleApplicationWhenTheEmployerRespondsLate() {
        JobApplication application = newApplication();
        application.markApplied(EFFECTIVE_DATE, USER, null);
        application.markStale(EFFECTIVE_DATE.plusDays(30), USER, "No response after a month");
        application.presumeDead(EFFECTIVE_DATE.plusDays(45), USER, null);

        application.beginInterviewing(EFFECTIVE_DATE.plusDays(50), USER, "Recruiter replied");

        assertEquals(ApplicationStatus.INTERVIEWING, application.status());
        assertEquals(ApplicationStatus.INTERVIEWING,
                application.transitionHistory().getLast().toStatus());
    }

    @Test
    void distinguishesDecliningAnOfferFromBeingRejectedAfterAnOffer() {
        JobApplication declinedApplication = offeredApplication();
        declinedApplication.decline(EFFECTIVE_DATE.plusDays(11), USER, "Accepted another offer");

        JobApplication rejectedApplication = offeredApplication();
        rejectedApplication.reject(EFFECTIVE_DATE.plusDays(11), "employer", "Offer withdrawn");

        assertEquals(ApplicationStatus.DECLINED, declinedApplication.status());
        assertEquals(ApplicationStatus.REJECTED, rejectedApplication.status());
    }

    @Test
    void rejectsIllegalTransitionsWithoutChangingStateOrHistory() {
        JobApplication application = newApplication();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> application.recordOffer(EFFECTIVE_DATE, USER, null));

        assertEquals("Cannot transition application from RECOMMENDED to OFFERED", exception.getMessage());
        assertEquals(ApplicationStatus.RECOMMENDED, application.status());
        assertFalse(application.transitionHistory().iterator().hasNext());
    }

    @Test
    void treatsAcceptedAsATerminalState() {
        JobApplication application = offeredApplication();
        application.accept(EFFECTIVE_DATE.plusDays(11), USER, null);

        assertThrows(
                IllegalStateException.class,
                () -> application.reject(EFFECTIVE_DATE.plusDays(12), "employer", "Late change"));

        assertEquals(ApplicationStatus.ACCEPTED, application.status());
        assertEquals(4, application.transitionHistory().size());
    }

    @Test
    void acceptsOnlyTheDocumentedLifecycleTransitions() {
        for (ApplicationStatus startingStatus : ApplicationStatus.values()) {
            for (ApplicationStatus targetStatus : ApplicationStatus.values()) {
                if (targetStatus == ApplicationStatus.RECOMMENDED) {
                    // RECOMMENDED is the initial state, not a transition command.
                    continue;
                }

                JobApplication application = applicationIn(startingStatus);
                int historySizeBeforeAttempt = application.transitionHistory().size();
                boolean allowed = ALLOWED_TRANSITIONS.get(startingStatus).contains(targetStatus);
                String transition = startingStatus + " -> " + targetStatus;

                if (allowed) {
                    performTransition(application, targetStatus);

                    assertEquals(targetStatus, application.status(), transition);
                    assertEquals(historySizeBeforeAttempt + 1, application.transitionHistory().size(), transition);
                } else {
                    assertThrows(
                            IllegalStateException.class,
                            () -> performTransition(application, targetStatus),
                            transition);

                    assertEquals(startingStatus, application.status(), transition);
                    assertEquals(historySizeBeforeAttempt, application.transitionHistory().size(), transition);
                }
            }
        }
    }

    private JobApplication newApplication() {
        return new JobApplication(UUID.randomUUID(), "Example Co.", "Java Engineer", CLOCK);
    }

    private JobApplication offeredApplication() {
        JobApplication application = newApplication();
        application.markApplied(EFFECTIVE_DATE, USER, null);
        application.beginInterviewing(EFFECTIVE_DATE.plusDays(3), USER, null);
        application.recordOffer(EFFECTIVE_DATE.plusDays(10), USER, null);
        return application;
    }

    private JobApplication applicationIn(ApplicationStatus status) {
        JobApplication application = newApplication();

        switch (status) {
            case RECOMMENDED -> {
                return application;
            }
            case APPLIED -> application.markApplied(EFFECTIVE_DATE, USER, null);
            case INTERVIEWING -> {
                application.markApplied(EFFECTIVE_DATE, USER, null);
                application.beginInterviewing(EFFECTIVE_DATE.plusDays(1), USER, null);
            }
            case OFFERED -> {
                application.markApplied(EFFECTIVE_DATE, USER, null);
                application.beginInterviewing(EFFECTIVE_DATE.plusDays(1), USER, null);
                application.recordOffer(EFFECTIVE_DATE.plusDays(2), USER, null);
            }
            case ACCEPTED -> {
                application.markApplied(EFFECTIVE_DATE, USER, null);
                application.beginInterviewing(EFFECTIVE_DATE.plusDays(1), USER, null);
                application.recordOffer(EFFECTIVE_DATE.plusDays(2), USER, null);
                application.accept(EFFECTIVE_DATE.plusDays(3), USER, null);
            }
            case STALE -> {
                application.markApplied(EFFECTIVE_DATE, USER, null);
                application.markStale(EFFECTIVE_DATE.plusDays(1), USER, null);
            }
            case PRESUMED_DEAD -> {
                application.markApplied(EFFECTIVE_DATE, USER, null);
                application.markStale(EFFECTIVE_DATE.plusDays(1), USER, null);
                application.presumeDead(EFFECTIVE_DATE.plusDays(2), USER, null);
            }
            case REJECTED -> {
                application.markApplied(EFFECTIVE_DATE, USER, null);
                application.reject(EFFECTIVE_DATE.plusDays(1), "employer", null);
            }
            case DECLINED -> {
                application.markApplied(EFFECTIVE_DATE, USER, null);
                application.beginInterviewing(EFFECTIVE_DATE.plusDays(1), USER, null);
                application.recordOffer(EFFECTIVE_DATE.plusDays(2), USER, null);
                application.decline(EFFECTIVE_DATE.plusDays(3), USER, null);
            }
        }

        return application;
    }

    private void performTransition(JobApplication application, ApplicationStatus targetStatus) {
        switch (targetStatus) {
            case APPLIED -> application.markApplied(EFFECTIVE_DATE.plusDays(60), USER, null);
            case INTERVIEWING -> application.beginInterviewing(EFFECTIVE_DATE.plusDays(60), USER, null);
            case OFFERED -> application.recordOffer(EFFECTIVE_DATE.plusDays(60), USER, null);
            case ACCEPTED -> application.accept(EFFECTIVE_DATE.plusDays(60), USER, null);
            case STALE -> application.markStale(EFFECTIVE_DATE.plusDays(60), USER, null);
            case PRESUMED_DEAD -> application.presumeDead(EFFECTIVE_DATE.plusDays(60), USER, null);
            case REJECTED -> application.reject(EFFECTIVE_DATE.plusDays(60), "employer", null);
            case DECLINED -> application.decline(EFFECTIVE_DATE.plusDays(60), USER, null);
            case RECOMMENDED -> throw new IllegalArgumentException("RECOMMENDED is not a transition target");
        }
    }
}
