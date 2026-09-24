package org.example.jobstuffagent.application;

import org.example.jobstuffagent.adapter.persistence.InMemoryJobApplicationRepository;
import org.example.jobstuffagent.domain.ApplicationStatus;
import org.example.jobstuffagent.domain.JobApplication;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobApplicationServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void createsAndStoresARecommendedApplicationWithAServerGeneratedId() {
        UUID serverGeneratedId = UUID.fromString("8a8a91a4-bb91-4f5d-9da6-cb4c4b23c9e9");
        JobApplicationService service = serviceWithId(serverGeneratedId);

        JobApplication application = service.create(new CreateJobApplicationCommand(
                "Example Co.",
                "Java Engineer",
                URI.create("https://careers.example.com/jobs/123")));

        assertEquals(serverGeneratedId, application.id());
        assertEquals(ApplicationStatus.RECOMMENDED, application.status());
        assertEquals("Example Co.", application.company());
        assertEquals("Java Engineer", application.role());
        assertEquals("https://careers.example.com/jobs/123", application.sourceUrl().orElseThrow().toString());
        assertSame(application, service.findById(serverGeneratedId).orElseThrow());
    }

    @Test
    void updatesExistingOpportunityDetails() {
        UUID id = UUID.fromString("0796d2ad-0ef4-4370-8e5d-3ca1f28e8f5c");
        JobApplicationService service = serviceWithId(id);
        service.create(new CreateJobApplicationCommand("Example Co.", "Engineer", null));

        JobApplication updated = service.update(id, new UpdateJobApplicationCommand(
                "Updated Co.",
                "Senior Java Engineer",
                URI.create("https://jobs.example.com/updated"))).orElseThrow();

        assertEquals("Updated Co.", updated.company());
        assertEquals("Senior Java Engineer", updated.role());
        assertEquals("https://jobs.example.com/updated", updated.sourceUrl().orElseThrow().toString());
        assertEquals(ApplicationStatus.RECOMMENDED, updated.status());
    }

    @Test
    void returnsEmptyForAnUpdateOfAnUnknownApplication() {
        JobApplicationService service = serviceWithId(UUID.randomUUID());

        assertTrue(service.update(
                UUID.randomUUID(),
                new UpdateJobApplicationCommand("Example Co.", "Engineer", null)).isEmpty());
    }

    @Test
    void listsAndDeletesApplications() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InMemoryJobApplicationRepository repository = new InMemoryJobApplicationRepository();
        JobApplicationService firstService = new JobApplicationService(repository, () -> firstId, CLOCK);
        JobApplicationService secondService = new JobApplicationService(repository, () -> secondId, CLOCK);
        firstService.create(new CreateJobApplicationCommand("First Co.", "Engineer", null));
        secondService.create(new CreateJobApplicationCommand("Second Co.", "Engineer", null));

        assertEquals(2, firstService.findAll().size());
        assertTrue(firstService.delete(firstId));
        assertFalse(firstService.delete(firstId));
        assertEquals(secondId, firstService.findAll().getFirst().id());
    }

    private JobApplicationService serviceWithId(UUID id) {
        return new JobApplicationService(new InMemoryJobApplicationRepository(), () -> id, CLOCK);
    }
}
