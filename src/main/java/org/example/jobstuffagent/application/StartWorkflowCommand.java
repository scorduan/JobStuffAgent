package org.example.jobstuffagent.application;

import java.util.UUID;

/**
 * A user prompt and an optional existing conversation to continue.
 */
public record StartWorkflowCommand(
        UUID conversationId,
        String prompt) {
}
