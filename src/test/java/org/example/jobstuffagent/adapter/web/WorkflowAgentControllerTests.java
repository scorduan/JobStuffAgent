package org.example.jobstuffagent.adapter.web;

import org.example.jobstuffagent.application.AgentClassification;
import org.example.jobstuffagent.application.AgentIntent;
import org.example.jobstuffagent.application.AgentPlanningResult;
import org.example.jobstuffagent.application.AgentSession;
import org.example.jobstuffagent.application.AgentWorkflowService;
import org.example.jobstuffagent.application.ApplicationLookupCriteria;
import org.example.jobstuffagent.application.ApplicationLookupError;
import org.example.jobstuffagent.application.ApplicationLookupErrorCode;
import org.example.jobstuffagent.application.ApplicationLookupResult;
import org.example.jobstuffagent.application.StartWorkflowCommand;
import org.example.jobstuffagent.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = WorkflowAgentController.class, properties = {
        "app.security.read-only-username=reader-user",
        "app.security.read-only-password=reader-password",
        "app.security.manager-username=manager-user",
        "app.security.manager-password=manager-password"
})
@Import(SecurityConfiguration.class)
class WorkflowAgentControllerTests {

    private static final UUID CONVERSATION_ID = UUID.fromString("b15d8d21-21eb-44c6-984d-7a8a76f631bd");
    private static final UUID SESSION_ID = UUID.fromString("8d4ca07d-bda3-4582-a59e-f4a867936c0e");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentWorkflowService agentWorkflowService;

    @Test
    void readOnlyUserCanStartAndReadTheReadOnlyWorkflow() throws Exception {
        AgentSession session = completedSession();
        when(agentWorkflowService.start(any(StartWorkflowCommand.class))).thenReturn(Optional.of(session));
        when(agentWorkflowService.findAllSessions()).thenReturn(List.of(session));
        when(agentWorkflowService.findSessionById(SESSION_ID)).thenReturn(Optional.of(session));

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("reader-user", "reader-password"))
                        .content("""
                                { "prompt": "List my applications" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/workflows/" + SESSION_ID))
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/workflows").with(httpBasic("reader-user", "reader-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(SESSION_ID.toString()));
        mockMvc.perform(get("/workflows/{id}", SESSION_ID)
                        .with(httpBasic("reader-user", "reader-password")))
                .andExpect(status().isOk());

        verify(agentWorkflowService).start(new StartWorkflowCommand(null, "List my applications"));
    }

    @Test
    void returnsNotFoundForAnUnknownConversationAndUnauthorizedWithoutCredentials() throws Exception {
        UUID unknownConversationId = UUID.fromString("b337b5a2-b4f2-4419-aee4-4a8b19e60d2f");
        when(agentWorkflowService.start(any(StartWorkflowCommand.class))).thenReturn(Optional.empty());

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("reader-user", "reader-password"))
                        .content("""
                                {
                                  "conversationId": "b337b5a2-b4f2-4419-aee4-4a8b19e60d2f",
                                  "prompt": "List my applications"
                                }
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/workflows"))
                .andExpect(status().isUnauthorized());

        verify(agentWorkflowService).start(new StartWorkflowCommand(
                unknownConversationId,
                "List my applications"));
    }

    @Test
    void returnsClassificationQuestionsToTheUi() throws Exception {
        AgentSession session = classificationNeedingInputSession();
        when(agentWorkflowService.start(any(StartWorkflowCommand.class))).thenReturn(Optional.of(session));

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("reader-user", "reader-password"))
                        .content("""
                                { "prompt": "What should I do next?" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED_NEEDS_INPUT"))
                .andExpect(jsonPath("$.stateHistory[2]").value("COMPLETED_NEEDS_INPUT"))
                .andExpect(jsonPath("$.questions[0]")
                        .value("Which application do you mean? Please include the company or role."));
    }

    @Test
    void returnsPlanningQuestionsAndLookupErrorsToTheUi() throws Exception {
        AgentSession session = planningNeedingInputSession();
        when(agentWorkflowService.start(any(StartWorkflowCommand.class))).thenReturn(Optional.of(session));

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(httpBasic("reader-user", "reader-password"))
                        .content("""
                                { "prompt": "What is the status of my Missing Co. application?" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED_NEEDS_INPUT"))
                .andExpect(jsonPath("$.stateHistory[4]").value("COMPLETED_NEEDS_INPUT"))
                .andExpect(jsonPath("$.lookupErrors[0].code").value("NO_MATCH"))
                .andExpect(jsonPath("$.questions[0]").value("No application matched the lookup criteria."));
    }

    private AgentSession completedSession() {
        AgentSession session = new AgentSession(
                SESSION_ID,
                CONVERSATION_ID,
                "List my applications",
                CLOCK.instant());
        AgentClassification classification = new AgentClassification(
                AgentIntent.LIST_APPLICATIONS,
                ApplicationLookupCriteria.none(),
                List.of(),
                "Found 0 application(s).");
        ApplicationLookupResult lookupResult = new ApplicationLookupResult(
                classification,
                List.of(),
                List.of(),
                "Found 0 application(s).");
        session.beginClassification();
        session.completeClassification(classification, CLOCK.instant());
        session.completeLookup(lookupResult);
        session.completePlanning(new AgentPlanningResult(List.of(), "Found 0 application(s)."), CLOCK.instant());
        session.completeValidation();
        session.completeExecution(lookupResult, CLOCK.instant());
        return session;
    }

    private AgentSession classificationNeedingInputSession() {
        AgentSession session = new AgentSession(
                SESSION_ID,
                CONVERSATION_ID,
                "What should I do next?",
                CLOCK.instant());
        session.beginClassification();
        session.completeClassification(new AgentClassification(
                AgentIntent.UNKNOWN,
                ApplicationLookupCriteria.none(),
                List.of("Which application do you mean? Please include the company or role."),
                "More information is required to identify an application."), CLOCK.instant());
        return session;
    }

    private AgentSession planningNeedingInputSession() {
        AgentSession session = new AgentSession(
                SESSION_ID,
                CONVERSATION_ID,
                "What is the status of my Missing Co. application?",
                CLOCK.instant());
        AgentClassification classification = new AgentClassification(
                AgentIntent.LOOK_UP_APPLICATION,
                new ApplicationLookupCriteria("Missing Co", null),
                List.of(),
                "The request is to look up one application.");
        ApplicationLookupResult lookupResult = new ApplicationLookupResult(
                classification,
                List.of(),
                List.of(new ApplicationLookupError(
                        ApplicationLookupErrorCode.NO_MATCH,
                        "No application matched the lookup criteria.")),
                "No application matched the lookup criteria.");
        session.beginClassification();
        session.completeClassification(classification, CLOCK.instant());
        session.completeLookup(lookupResult);
        session.completePlanning(new AgentPlanningResult(
                List.of("No application matched the lookup criteria."),
                "More information is required to continue."), CLOCK.instant());
        return session;
    }
}
