package org.example.jobstuffagent.application;

import java.util.UUID;

/**
 * Allocates server-owned job-application identifiers.
 */
@FunctionalInterface
public interface JobApplicationIdGenerator {

    UUID nextId();
}
