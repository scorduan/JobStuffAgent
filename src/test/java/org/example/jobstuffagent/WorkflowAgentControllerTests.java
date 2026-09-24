package org.example.jobstuffagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.example.jobstuffagent.config.SecurityConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(value = WorkflowAgentController.class, properties = {
        "app.security.read-only-username=reader-user",
        "app.security.read-only-password=reader-password",
        "app.security.manager-username=manager-user",
        "app.security.manager-password=manager-password"
})
@Import(SecurityConfiguration.class)
class WorkflowAgentControllerTests {
    @Autowired
    MockMvc mockMvc;

    @Test
    void listsWorkflows() throws Exception {
        mockMvc.perform(get("/workflows").with(httpBasic("reader-user", "reader-password")))
                .andExpect(status().isOk())
                .andExpect(content().string("None"));
    }

    @Test
    void executesWorkflow() throws Exception {
        mockMvc.perform(post("/workflows").with(httpBasic("reader-user", "reader-password")))
                .andExpect(status().isOk())
                .andExpect(content().string("N/A"));
    }
}
