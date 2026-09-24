package org.example.jobstuffagent.application;

import java.net.URI;

/**
 * Replacement values for the editable opportunity information on an application.
 */
public record UpdateJobApplicationCommand(
        String company,
        String role,
        URI sourceUrl) {
}
