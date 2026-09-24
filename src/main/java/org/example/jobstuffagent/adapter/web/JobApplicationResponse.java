package org.example.jobstuffagent.adapter.web;

import org.example.jobstuffagent.domain.ApplicationStatus;
import org.example.jobstuffagent.domain.JobApplication;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record JobApplicationResponse(
        UUID id,
        String company,
        String role,
        URI sourceUrl,
        ApplicationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static JobApplicationResponse from(JobApplication application) {
        return new JobApplicationResponse(
                application.id(),
                application.company(),
                application.role(),
                application.sourceUrl().orElse(null),
                application.status(),
                application.createdAt(),
                application.updatedAt());
    }
}
