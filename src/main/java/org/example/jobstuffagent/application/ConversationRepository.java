package org.example.jobstuffagent.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for user-visible conversation threads.
 */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(UUID id);
}
