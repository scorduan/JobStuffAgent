package org.example.jobstuffagent.adapter.web;

import jakarta.validation.Valid;
import org.example.jobstuffagent.application.CreateJobApplicationCommand;
import org.example.jobstuffagent.application.JobApplicationService;
import org.example.jobstuffagent.application.UpdateJobApplicationCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/applications")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    public JobApplicationController(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    @PostMapping
    public ResponseEntity<JobApplicationResponse> create(
            @Valid @RequestBody CreateJobApplicationRequest request) {
        JobApplicationResponse response = JobApplicationResponse.from(jobApplicationService.create(
                new CreateJobApplicationCommand(request.company(), request.role(), request.sourceUrl())));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<JobApplicationResponse> findAll() {
        return jobApplicationService.findAll().stream()
                .map(JobApplicationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> findById(@PathVariable UUID id) {
        return jobApplicationService.findById(id)
                .map(JobApplicationResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJobApplicationRequest request) {
        return jobApplicationService.update(
                        id,
                        new UpdateJobApplicationCommand(request.company(), request.role(), request.sourceUrl()))
                .map(JobApplicationResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return jobApplicationService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
