package org.example.jobstuffagent.adapter.persistence;

import org.example.jobstuffagent.application.JobApplicationRepository;
import org.example.jobstuffagent.domain.JobApplication;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A development-only repository. Its contents disappear when the process stops.
 */
@Repository
public class InMemoryJobApplicationRepository implements JobApplicationRepository {

    private final ConcurrentMap<UUID, JobApplication> applications = new ConcurrentHashMap<>();

    @Override
    public JobApplication save(JobApplication application) {
        applications.put(application.id(), application);
        return application;
    }

    @Override
    public Optional<JobApplication> findById(UUID id) {
        return Optional.ofNullable(applications.get(id));
    }

    @Override
    public List<JobApplication> findAll() {
        return applications.values().stream()
                .sorted(Comparator.comparing(JobApplication::createdAt).thenComparing(JobApplication::id))
                .toList();
    }

    @Override
    public boolean deleteById(UUID id) {
        return applications.remove(id) != null;
    }
}
