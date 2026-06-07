package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.Conversation;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.model.ToolResult;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class MongoChatHistoryAdapter implements ChatHistoryPort {

    private final MongoConversationRepository conversationRepository;
    private final MongoMessageRepository messageRepository;

    public MongoChatHistoryAdapter(
            MongoConversationRepository conversationRepository,
            MongoMessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public List<Message> findRecentMessages(String conversationId, int limit) {
        List<MessageDocument> documents = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId)
                .take(limit)
                .collectList()
                .block(); // Safe to block here because ChatOrchestrator runs on boundedElastic

        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        // We fetched newest first, but the LLM needs chronological order (oldest first)
        Collections.reverse(documents);

        return documents.stream()
                .map(this::toDomainMessage)
                .toList();
    }

    @Override
    public void saveMessage(Message message) {
        MessageDocument document = toMessageDocument(message);
        messageRepository.save(document).block();
    }

    @Override
    public Conversation saveConversation(Conversation conversation) {
        ConversationDocument doc = new ConversationDocument(
                conversation.id(),
                conversation.userId(),
                conversation.title(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
        conversationRepository.save(doc).block();
        return conversation;
    }

    @Override
    public Optional<Conversation> findConversationById(String conversationId) {
        ConversationDocument doc = conversationRepository.findById(conversationId).block();
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(new Conversation(
                doc.id(),
                doc.userId(),
                doc.title(),
                doc.createdAt(),
                doc.updatedAt()
        ));
    }

    // --- Mappers ---

    private Message toDomainMessage(MessageDocument doc) {
        List<ToolCall> toolCalls = doc.toolCalls().stream()
                .map(tc -> new ToolCall(tc.id(), tc.name(), tc.argumentsJson()))
                .toList();

        List<ToolResult> toolResults = doc.toolResults().stream()
                .map(tr -> new ToolResult(tr.toolCallId(), tr.content()))
                .toList();

        return new Message(
                doc.id(),
                doc.conversationId(),
                doc.role(),
                doc.content(),
                toolCalls,
                toolResults,
                doc.createdAt()
        );
    }

    private MessageDocument toMessageDocument(Message message) {
        List<MessageDocument.ToolCallDocument> toolCallDocs = message.toolCalls().stream()
                .map(tc -> new MessageDocument.ToolCallDocument(tc.id(), tc.name(), tc.argumentsJson()))
                .toList();

        List<MessageDocument.ToolResultDocument> toolResultDocs = message.toolResults().stream()
                .map(tr -> new MessageDocument.ToolResultDocument(tr.toolCallId(), tr.content()))
                .toList();

        return new MessageDocument(
                message.id(),
                message.conversationId(),
                message.role(),
                message.content(),
                toolCallDocs,
                toolResultDocs,
                message.createdAt()
        );
    }
}