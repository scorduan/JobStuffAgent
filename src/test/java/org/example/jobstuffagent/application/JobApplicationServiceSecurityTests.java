package org.example.jobstuffagent.application;

import org.example.jobstuffagent.domain.JobApplication;
import org.example.jobstuffagent.config.SecurityConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.security.read-only-username=reader-user",
        "app.security.read-only-password=reader-password",
        "app.security.manager-username=manager-user",
        "app.security.manager-password=manager-password"
})
class JobApplicationServiceSecurityTests {

    @Autowired
    private JobApplicationService jobApplicationService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readOnlyRoleCanReadBothLookupMethods() {
        authenticateAs(SecurityConfiguration.APPLICATIONS_READ_ONLY);

        assertDoesNotThrow(() -> jobApplicationService.findAll());
        assertDoesNotThrow(() -> jobApplicationService.findById(UUID.randomUUID()));
    }

    @Test
    void readOnlyRoleCannotUseAnyWriteMethod() {
        authenticateAs(SecurityConfiguration.APPLICATIONS_READ_ONLY);

        assertThrows(AccessDeniedException.class, () -> jobApplicationService.create(command()));
        assertThrows(AccessDeniedException.class,
                () -> jobApplicationService.update(UUID.randomUUID(), updateCommand()));
        assertThrows(AccessDeniedException.class, () -> jobApplicationService.delete(UUID.randomUUID()));
    }

    @Test
    void managerRoleCanUseEveryCrudMethod() {
        authenticateAs(SecurityConfiguration.APPLICATIONS_MANAGER);

        JobApplication created = jobApplicationService.create(command());
        assertFalse(jobApplicationService.findAll().isEmpty());
        assertTrue(jobApplicationService.findById(created.id()).isPresent());
        assertTrue(jobApplicationService.update(created.id(), updateCommand()).isPresent());
        assertTrue(jobApplicationService.delete(created.id()));
    }

    private void authenticateAs(String role) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "test-user",
                null,
                new SimpleGrantedAuthority("ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private CreateJobApplicationCommand command() {
        return new CreateJobApplicationCommand("Example Co.", "Java Engineer", null);
    }

    private UpdateJobApplicationCommand updateCommand() {
        return new UpdateJobApplicationCommand("Updated Co.", "Senior Java Engineer", null);
    }
}
