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
        transitionTo(ApplicationStatus.APPLIED, effectiveDate, source, note);
    }

    public void beginInterviewing(LocalDate effectiveDate, String source, String note) {
        transitionTo(ApplicationStatus.INTERVIEWING, effectiveDate, source, note);
    }

    public void recordOffer(LocalDate effectiveDate, String source, String note) {
        transitionTo(ApplicationStatus.OFFERED, effectiveDate, source, note);
    }

    public void accept(LocalDate effectiveDate, String source, String note) {
        transitionTo(ApplicationStatus.ACCEPTED, effectiveDate, source, note);
    }

    public void decline(LocalDate effectiveDate, String source, String note) {
        transitionTo(ApplicationStatus.DECLINED, effectiveDate, source, note);
    }

    public void reject(LocalDate effectiveDate, String source, String note) {
        transitionTo(ApplicationStatus.REJECTED, effectiveDate, source, note);
    }

    public void markStale(LocalDate effectiveDate, String source, String note) {
        transitionTo(ApplicationStatus.STALE, effectiveDate, source, note);
    }

    public void presumeDead(LocalDate effectiveDate, String source, String note) {
        transitionTo(ApplicationStatus.PRESUMED_DEAD, effectiveDate, source, note);
    }

    private void transitionTo(
            ApplicationStatus targetStatus,
            LocalDate effectiveDate,
            String source,
            String note) {
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");

        if (!isAllowed(status, targetStatus)) {
            throw new IllegalStateException(
                    "Cannot transition application from " + status + " to " + targetStatus);
        }

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

    private static boolean isAllowed(ApplicationStatus currentStatus, ApplicationStatus targetStatus) {
        return switch (currentStatus) {
            case RECOMMENDED -> targetStatus == ApplicationStatus.APPLIED;
            case APPLIED -> targetStatus == ApplicationStatus.INTERVIEWING
                    || targetStatus == ApplicationStatus.STALE
                    || targetStatus == ApplicationStatus.REJECTED;
            case INTERVIEWING -> targetStatus == ApplicationStatus.OFFERED
                    || targetStatus == ApplicationStatus.REJECTED;
            case OFFERED -> targetStatus == ApplicationStatus.ACCEPTED
                    || targetStatus == ApplicationStatus.DECLINED
                    || targetStatus == ApplicationStatus.REJECTED;
            case STALE -> targetStatus == ApplicationStatus.PRESUMED_DEAD
                    || targetStatus == ApplicationStatus.INTERVIEWING
                    || targetStatus == ApplicationStatus.REJECTED;
            case PRESUMED_DEAD -> targetStatus == ApplicationStatus.INTERVIEWING
                    || targetStatus == ApplicationStatus.REJECTED;
            case ACCEPTED, DECLINED, REJECTED -> false;
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
