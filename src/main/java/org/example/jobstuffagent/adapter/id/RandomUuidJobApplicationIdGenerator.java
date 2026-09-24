package org.example.jobstuffagent.adapter.id;

import org.example.jobstuffagent.application.JobApplicationIdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The local implementation of server-side identifier allocation.
 */
@Component
public class RandomUuidJobApplicationIdGenerator implements JobApplicationIdGenerator {

    @Override
    public UUID nextId() {
        return UUID.randomUUID();
    }
}
