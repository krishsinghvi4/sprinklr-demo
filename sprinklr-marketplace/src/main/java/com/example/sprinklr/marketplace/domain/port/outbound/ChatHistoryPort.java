package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.chat.Conversation;
import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.example.sprinklr.marketplace.domain.model.chat.PagedResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatHistoryPort {

    List<Message> findRecentMessages(String conversationId, int limit);

    /**
     * Returns chronological messages older than {@code before}, or the latest page when {@code before} is null.
     */
    List<Message> findMessagesBefore(String conversationId, int limit, Instant before);

    /**
     * Returns chronological messages spanning the last {@code turnLimit} user-initiated turns.
     * Each turn starts at a USER message and includes all subsequent rows until the next USER message.
     */
    List<Message> findRecentTurns(String conversationId, int turnLimit);

    void saveMessage(Message message);

    /**
     * Replaces tool result payloads with a short stub after the turn summary is persisted.
     */
    void truncateToolResults(String messageId);

    Conversation saveConversation(Conversation conversation);

    Optional<Conversation> findConversationById(String conversationId);

    Optional<Conversation> findConversationByIdAndUserId(String conversationId, String userId);

    List<Conversation> findConversationsByUserId(String userId);

    PagedResult<Conversation> findConversationsByUserIdPaged(String userId, int page, int size);

    Optional<String> findFirstUserMessageContent(String conversationId);

    void touchConversation(String conversationId, String preview);

    void deleteConversation(String conversationId, String userId);

    /**
     * Collects tool result payloads from the chat turn that produced {@code assistantMessageId},
     * for grounding dashboard extended insights. Returns empty if the message is unknown.
     */
    Optional<String> collectToolResultSnapshotForAssistantMessage(
            String conversationId,
            String assistantMessageId
    );
}
