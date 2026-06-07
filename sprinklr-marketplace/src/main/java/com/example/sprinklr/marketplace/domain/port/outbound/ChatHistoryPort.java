package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.Conversation;
import com.example.sprinklr.marketplace.domain.model.Message;

import java.util.List;
import java.util.Optional;

public interface ChatHistoryPort {

    List<Message> findRecentMessages(String conversationId, int limit);

    void saveMessage(Message message);

    Conversation saveConversation(Conversation conversation);

    Optional<Conversation> findConversationById(String conversationId);
}
