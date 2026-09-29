package org.example.jobstuffagent.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.read-only-username=reader-user",
        "app.security.read-only-password=reader-password",
        "app.security.manager-username=manager-user",
        "app.security.manager-password=manager-password"
})
@AutoConfigureMockMvc
class WorkflowAgentIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void managerCanExecuteAStatusTransitionThroughTheWorkflowEndpoint() throws Exception {
        String company = "Workflow Co " + UUID.randomUUID();
        MvcResult creation = mockMvc.perform(post("/applications")
                        .with(httpBasic("manager-user", "manager-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "company": "%s", "role": "Integration Engineer" }
                                """.formatted(company)))
                .andExpect(status().isCreated())
                .andReturn();
        String location = creation.getResponse().getHeader("Location");
        UUID applicationId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(post("/workflows")
                        .with(httpBasic("manager-user", "manager-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "prompt": "Mark my %s application as APPLIED" }
                                """.formatted(company)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.selectedApplicationIds[0]").value(applicationId.toString()))
                .andExpect(jsonPath("$.toolExecutions[0].status").value("SUCCEEDED"));

        mockMvc.perform(get("/applications/{id}", applicationId)
                        .with(httpBasic("manager-user", "manager-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }
}
