package org.example.jobstuffagent.adapter.web;

import jakarta.validation.constraints.NotBlank;

import java.net.URI;

public record UpdateJobApplicationRequest(
        @NotBlank String company,
        @NotBlank String role,
        URI sourceUrl) {
}
