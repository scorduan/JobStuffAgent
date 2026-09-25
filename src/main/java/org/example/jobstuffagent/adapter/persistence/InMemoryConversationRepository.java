package org.example.jobstuffagent.adapter.persistence;

import org.example.jobstuffagent.application.Conversation;
import org.example.jobstuffagent.application.ConversationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A development-only conversation store. Its contents disappear when the process stops.
 */
@Repository
public class InMemoryConversationRepository implements ConversationRepository {

    private final ConcurrentMap<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    @Override
    public Conversation save(Conversation conversation) {
        conversations.put(conversation.id(), conversation);
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        return Optional.ofNullable(conversations.get(id));
    }
}
