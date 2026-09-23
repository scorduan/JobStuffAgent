package org.example.jobstuffagent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.PublicKey;

@RestController
@RequestMapping("/workflows")
public class WorkflowAgentController {
   @GetMapping("")
    public String ListSessionsMethod() {
       return "None";
   }

   @PostMapping("")
    public String ExecuteWorkflowAgent() {
       return "N/A";
   }




}
