package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for authoritative job-application records.
 */
public interface JobApplicationRepository {

    JobApplication save(JobApplication application);

    Optional<JobApplication> findById(UUID id);

    List<JobApplication> findAll();

    boolean deleteById(UUID id);
}
