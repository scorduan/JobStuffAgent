package org.example.jobstuffagent.adapter.web;

import org.example.jobstuffagent.application.CreateJobApplicationCommand;
import org.example.jobstuffagent.application.JobApplicationService;
import org.example.jobstuffagent.application.UpdateJobApplicationCommand;
import org.example.jobstuffagent.config.SecurityConfiguration;
import org.example.jobstuffagent.domain.JobApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = JobApplicationController.class, properties = {
        "app.security.read-only-username=reader-user",
        "app.security.read-only-password=reader-password",
        "app.security.manager-username=manager-user",
        "app.security.manager-password=manager-password"
})
@Import(SecurityConfiguration.class)
class JobApplicationControllerTests {

    private static final UUID APPLICATION_ID = UUID.fromString("a1b48cf4-e56e-4d5c-ad70-1f482f063390");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobApplicationService jobApplicationService;

    @Test
    void createsAnApplicationFromAValidatedRequestWithoutAClientId() throws Exception {
        when(jobApplicationService.create(any(CreateJobApplicationCommand.class)))
                .thenReturn(application());

        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("manager-user", "manager-password"))
                        .content("""
                                {
                                  "company": "Example Co.",
                                  "role": "Java Engineer",
                                  "sourceUrl": "https://careers.example.com/jobs/123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/applications/" + APPLICATION_ID))
                .andExpect(jsonPath("$.id").value(APPLICATION_ID.toString()))
                .andExpect(jsonPath("$.company").value("Example Co."))
                .andExpect(jsonPath("$.role").value("Java Engineer"))
                .andExpect(jsonPath("$.status").value("RECOMMENDED"));

        verify(jobApplicationService).create(new CreateJobApplicationCommand(
                "Example Co.",
                "Java Engineer",
                URI.create("https://careers.example.com/jobs/123")));
    }

    @Test
    void rejectsAnInvalidCreateRequestBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("manager-user", "manager-password"))
                        .content("""
                                {
                                  "company": " ",
                                  "role": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void returnsApplicationsAndReportsMissingRecordsAsNotFound() throws Exception {
        when(jobApplicationService.findAll()).thenReturn(List.of(application()));
        when(jobApplicationService.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
        UUID missingId = UUID.fromString("a41dd5e5-4a6b-4ba0-a0d1-2f39d0c5ca2d");
        when(jobApplicationService.findById(missingId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/applications").with(httpBasic("manager-user", "manager-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(APPLICATION_ID.toString()));
        mockMvc.perform(get("/applications/{id}", APPLICATION_ID)
                        .with(httpBasic("manager-user", "manager-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Example Co."));
        mockMvc.perform(get("/applications/{id}", missingId)
                        .with(httpBasic("manager-user", "manager-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesAndDeletesApplicationsWhenTheyExist() throws Exception {
        when(jobApplicationService.update(eq(APPLICATION_ID), any(UpdateJobApplicationCommand.class)))
                .thenReturn(Optional.of(application()));
        when(jobApplicationService.delete(APPLICATION_ID)).thenReturn(true);

        mockMvc.perform(put("/applications/{id}", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("manager-user", "manager-password"))
                        .content("""
                                {
                                  "company": "Updated Co.",
                                  "role": "Senior Java Engineer",
                                  "sourceUrl": "https://jobs.example.com/updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(APPLICATION_ID.toString()));
        mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
                        .with(httpBasic("manager-user", "manager-password")))
                .andExpect(status().isNoContent());

        verify(jobApplicationService).update(
                APPLICATION_ID,
                new UpdateJobApplicationCommand(
                        "Updated Co.",
                        "Senior Java Engineer",
                        URI.create("https://jobs.example.com/updated")));
        verify(jobApplicationService).delete(APPLICATION_ID);
    }

    @Test
    void reportsMissingApplicationsWhenUpdatingOrDeleting() throws Exception {
        UUID missingId = UUID.fromString("bcc153a7-4e2b-44a1-9d44-4940bce21b0d");
        when(jobApplicationService.update(eq(missingId), any(UpdateJobApplicationCommand.class)))
                .thenReturn(Optional.empty());
        when(jobApplicationService.delete(missingId)).thenReturn(false);

        mockMvc.perform(put("/applications/{id}", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("manager-user", "manager-password"))
                        .content("""
                                {
                                  "company": "Example Co.",
                                  "role": "Java Engineer"
                                }
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/applications/{id}", missingId)
                        .with(httpBasic("manager-user", "manager-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readOnlyUserCanReadButCannotWrite() throws Exception {
        when(jobApplicationService.findAll()).thenReturn(List.of(application()));

        mockMvc.perform(get("/applications").with(httpBasic("reader-user", "reader-password")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("reader-user", "reader-password"))
                        .content("""
                                { "company": "Example Co.", "role": "Java Engineer" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/applications/{id}", APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("reader-user", "reader-password"))
                        .content("""
                                { "company": "Example Co.", "role": "Java Engineer" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/applications/{id}", APPLICATION_ID)
                        .with(httpBasic("reader-user", "reader-password")))
                .andExpect(status().isForbidden());

        verify(jobApplicationService).findAll();
    }

    private JobApplication application() {
        return new JobApplication(
                APPLICATION_ID,
                "Example Co.",
                "Java Engineer",
                URI.create("https://careers.example.com/jobs/123"),
                CLOCK);
    }
}
