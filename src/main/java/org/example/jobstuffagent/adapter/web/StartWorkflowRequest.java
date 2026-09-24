package org.example.jobstuffagent.adapter.web;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record StartWorkflowRequest(
        UUID conversationId,
        @NotBlank String prompt) {
}
