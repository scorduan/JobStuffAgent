package org.example.jobstuffagent.application;

import java.net.URI;

/**
 * Data required to create a user-curated recommended job application.
 */
public record CreateJobApplicationCommand(
        String company,
        String role,
        URI sourceUrl) {
}
