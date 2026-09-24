package org.example.jobstuffagent.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Development-only local authentication settings. Values come from environment variables,
 * never from a committed password.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record LocalSecurityProperties(
        @NotBlank String readOnlyUsername,
        @NotBlank String readOnlyPassword,
        @NotBlank String managerUsername,
        @NotBlank String managerPassword) {
}
