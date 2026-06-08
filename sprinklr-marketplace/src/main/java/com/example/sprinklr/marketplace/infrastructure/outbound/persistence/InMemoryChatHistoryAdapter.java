package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.Conversation;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation for testing without MongoDB.
 * This is disabled - using MongoChatHistoryAdapter instead.
 * Set @Primary annotation to use this adapter.
 */
//@Component
//@Primary
public class InMemoryChatHistoryAdapter implements ChatHistoryPort {

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, List<Message>> conversationMessages = new ConcurrentHashMap<>();

    @Override
    public List<Message> findRecentMessages(String conversationId, int limit) {
        List<Message> messages = conversationMessages.getOrDefault(conversationId, new ArrayList<>());
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    @Override
    public void saveMessage(Message message) {
        conversationMessages
                .computeIfAbsent(message.conversationId(), k -> new ArrayList<>())
                .add(message);
    }

    @Override
    public Conversation saveConversation(Conversation conversation) {
        conversations.put(conversation.id(), conversation);
        conversationMessages.putIfAbsent(conversation.id(), new ArrayList<>());
        return conversation;
    }

    @Override
    public Optional<Conversation> findConversationById(String conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }
}
