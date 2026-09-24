package org.example.jobstuffagent.adapter.web;

import jakarta.validation.Valid;
import org.example.jobstuffagent.application.AgentWorkflowService;
import org.example.jobstuffagent.application.StartWorkflowCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/workflows")
public class WorkflowAgentController {

    private final AgentWorkflowService agentWorkflowService;

    public WorkflowAgentController(AgentWorkflowService agentWorkflowService) {
        this.agentWorkflowService = agentWorkflowService;
    }

    @PostMapping
    public ResponseEntity<AgentSessionResponse> start(@Valid @RequestBody StartWorkflowRequest request) {
        return agentWorkflowService.start(new StartWorkflowCommand(request.conversationId(), request.prompt()))
                .map(session -> {
                    AgentSessionResponse response = AgentSessionResponse.from(session);
                    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                            .path("/{id}")
                            .buildAndExpand(response.sessionId())
                            .toUri();
                    return ResponseEntity.created(location).body(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<AgentSessionResponse> findAll() {
        return agentWorkflowService.findAllSessions().stream()
                .map(AgentSessionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentSessionResponse> findById(@PathVariable UUID id) {
        return agentWorkflowService.findSessionById(id)
                .map(AgentSessionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
