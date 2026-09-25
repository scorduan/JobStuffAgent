package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.example.jobstuffagent.domain.ApplicationStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates application use cases while leaving lifecycle rules inside {@link JobApplication}.
 */
@Service
public class JobApplicationService {

    private final JobApplicationRepository repository;
    private final JobApplicationIdGenerator idGenerator;
    private final Clock clock;

    public JobApplicationService(
            JobApplicationRepository repository,
            JobApplicationIdGenerator idGenerator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @PreAuthorize("hasRole('APPLICATIONS_MANAGER')")
    public JobApplication create(CreateJobApplicationCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        JobApplication application = new JobApplication(
                idGenerator.nextId(),
                command.company(),
                command.role(),
                command.sourceUrl(),
                clock);
        return repository.save(application);
    }

    @PreAuthorize("hasAnyRole('APPLICATIONS_READ_ONLY', 'APPLICATIONS_MANAGER')")
    public List<JobApplication> findAll() {
        return repository.findAll();
    }

    @PreAuthorize("hasAnyRole('APPLICATIONS_READ_ONLY', 'APPLICATIONS_MANAGER')")
    public Optional<JobApplication> findById(UUID id) {
        return repository.findById(id);
    }

    @PreAuthorize("hasRole('APPLICATIONS_MANAGER')")
    public Optional<JobApplication> update(UUID id, UpdateJobApplicationCommand command) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return repository.findById(id)
                .map(application -> {
                    application.updateOpportunity(command.company(), command.role(), command.sourceUrl());
                    return repository.save(application);
                });
    }

    @PreAuthorize("hasRole('APPLICATIONS_MANAGER')")
    public boolean delete(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.deleteById(id);
    }

    @PreAuthorize("hasRole('APPLICATIONS_MANAGER')")
    public JobApplication transitionStatus(
            UUID id,
            ApplicationStatus targetStatus,
            LocalDate effectiveDate,
            String source,
            String note) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");

        JobApplication application = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application does not exist: " + id));
        switch (targetStatus) {
            case APPLIED -> application.markApplied(effectiveDate, source, note);
            case INTERVIEWING -> application.beginInterviewing(effectiveDate, source, note);
            case OFFERED -> application.recordOffer(effectiveDate, source, note);
            case ACCEPTED -> application.accept(effectiveDate, source, note);
            case DECLINED -> application.decline(effectiveDate, source, note);
            case REJECTED -> application.reject(effectiveDate, source, note);
            case STALE -> application.markStale(effectiveDate, source, note);
            case PRESUMED_DEAD -> application.presumeDead(effectiveDate, source, note);
            case RECOMMENDED -> throw new IllegalArgumentException(
                    "RECOMMENDED is not a transition target: " + id);
        }
        return repository.save(application);
    }
}
