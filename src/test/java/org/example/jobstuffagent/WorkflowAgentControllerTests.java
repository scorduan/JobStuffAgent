package org.example.jobstuffagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(WorkflowAgentController.class)
class WorkflowAgentControllerTests {
    @Autowired
    MockMvc mockMvc;

    @Test
    void listsWorkflows() throws Exception {
        mockMvc.perform(get("/workflows"))
                .andExpect(status().isOk())
                .andExpect(content().string("None"));
    }

    @Test
    void executesWorkflow() throws Exception {
        mockMvc.perform(post("/workflows"))
                .andExpect(status().isOk())
                .andExpect(content().string("N/A"));
    }
}
