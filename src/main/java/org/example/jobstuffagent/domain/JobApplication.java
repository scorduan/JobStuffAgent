package org.example.jobstuffagent.domain;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The authoritative record for one job opportunity and its application lifecycle.
 *
 * <p>Only this class can change the lifecycle state. Each accepted transition is
 * appended to {@link #transitionHistory()} for later audit and date-based reporting.</p>
 */
public final class JobApplication {

    private final UUID id;
    private final String company;
    private final String role;
    private final URI sourceUrl;
    private final Instant createdAt;
    private final Clock clock;
    private final List<ApplicationTransition> transitionHistory = new ArrayList<>();

    private ApplicationStatus status = ApplicationStatus.RECOMMENDED;

    public JobApplication(UUID id, String company, String role, Clock clock) {
        this(id, company, role, null, clock);
    }

    public JobApplication(UUID id, String company, String role, URI sourceUrl, Clock clock) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.company = requireText(company, "company");
        this.role = requireText(role, "role");
        this.sourceUrl = sourceUrl;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.createdAt = clock.instant();
    }

    public UUID id() {
        return id;
    }

    public String company() {
        return company;
    }

    public String role() {
        return role;
    }

    public Optional<URI> sourceUrl() {
        return Optional.ofNullable(sourceUrl);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ApplicationStatus status() {
        return status;
    }

    public List<ApplicationTransition> transitionHistory() {
        return List.copyOf(transitionHistory);
    }

    public void markApplied(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(ApplicationStatus.APPLIED, ApplicationStatus.RECOMMENDED);
        recordTransition(ApplicationStatus.APPLIED, effectiveDate, source, note);
    }

    public void beginInterviewing(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(
                ApplicationStatus.INTERVIEWING,
                ApplicationStatus.APPLIED,
                ApplicationStatus.STALE,
                ApplicationStatus.PRESUMED_DEAD);
        recordTransition(ApplicationStatus.INTERVIEWING, effectiveDate, source, note);
    }

    public void recordOffer(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(ApplicationStatus.OFFERED, ApplicationStatus.INTERVIEWING);
        recordTransition(ApplicationStatus.OFFERED, effectiveDate, source, note);
    }

    public void accept(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(ApplicationStatus.ACCEPTED, ApplicationStatus.OFFERED);
        recordTransition(ApplicationStatus.ACCEPTED, effectiveDate, source, note);
    }

    public void decline(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(ApplicationStatus.DECLINED, ApplicationStatus.OFFERED);
        recordTransition(ApplicationStatus.DECLINED, effectiveDate, source, note);
    }

    public void reject(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(
                ApplicationStatus.REJECTED,
                ApplicationStatus.APPLIED,
                ApplicationStatus.INTERVIEWING,
                ApplicationStatus.OFFERED,
                ApplicationStatus.STALE,
                ApplicationStatus.PRESUMED_DEAD);
        recordTransition(ApplicationStatus.REJECTED, effectiveDate, source, note);
    }

    public void markStale(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(ApplicationStatus.STALE, ApplicationStatus.APPLIED);
        recordTransition(ApplicationStatus.STALE, effectiveDate, source, note);
    }

    public void presumeDead(LocalDate effectiveDate, String source, String note) {
        verifyTransitionTo(ApplicationStatus.PRESUMED_DEAD, ApplicationStatus.STALE);
        recordTransition(ApplicationStatus.PRESUMED_DEAD, effectiveDate, source, note);
    }

    private void verifyTransitionTo(
            ApplicationStatus targetStatus,
            ApplicationStatus... allowedSourceStatuses) {
        for (ApplicationStatus allowedSourceStatus : allowedSourceStatuses) {
            if (status == allowedSourceStatus) {
                return;
            }
        }

        throw new IllegalStateException(
                "Cannot transition application from " + status + " to " + targetStatus);
    }

    private void recordTransition(
            ApplicationStatus targetStatus,
            LocalDate effectiveDate,
            String source,
            String note) {
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        ApplicationTransition transition = new ApplicationTransition(
                status,
                targetStatus,
                effectiveDate,
                clock.instant(),
                source,
                note);

        status = targetStatus;
        transitionHistory.add(transition);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
