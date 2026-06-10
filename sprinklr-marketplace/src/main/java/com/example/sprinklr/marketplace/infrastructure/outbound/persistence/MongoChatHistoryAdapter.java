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

    private final ConversationRepository conversationRepository;
    private final MongoMessageRepository messageRepository;

    public MongoChatHistoryAdapter(
            ConversationRepository conversationRepository,
            MongoMessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public List<Message> findRecentMessages(String conversationId, int limit) {
        System.out.println("[MongoDB] Finding recent messages for conversation: " + conversationId + " (limit: " + limit + ")");
        
        try {
            List<MessageDocument> documents = messageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId)
                    .take(limit)
                    .collectList()
                    .block(); // Safe to block here because ChatOrchestrator runs on boundedElastic

            if (documents == null) {
                System.out.println("[MongoDB] Query returned null");
                return List.of();
            }
            
            System.out.println("[MongoDB] Found " + documents.size() + " messages");

            if (documents.isEmpty()) {
                System.out.println("[MongoDB] No messages found for conversation: " + conversationId);
                return List.of();
            }

            // We fetched newest first, but the LLM needs chronological order (oldest first)
            Collections.reverse(documents);

            List<Message> result = documents.stream()
                    .map(this::toDomainMessage)
                    .toList();
            
            System.out.println("[MongoDB] Converted to " + result.size() + " domain messages");
            return result;
        } catch (Exception e) {
            System.err.println("[MongoDB] Error finding messages for conversation " + conversationId + ": " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public void saveMessage(Message message) {
        System.out.println("[MongoDB] Saving message - ID: " + message.id() + 
                ", Conversation: " + message.conversationId() + 
                ", Role: " + message.role() + 
                ", Content length: " + (message.content() != null ? message.content().length() : 0));
        
        try {
            MessageDocument document = toMessageDocument(message);
            messageRepository.save(document).block();
            System.out.println("[MongoDB] Message saved successfully");
        } catch (Exception e) {
            System.err.println("[MongoDB] Error saving message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public Conversation saveConversation(Conversation conversation) {
        System.out.println("[MongoDB] Saving conversation - ID: " + conversation.id() + 
                ", User: " + conversation.userId());
        
        ConversationDocument doc = new ConversationDocument(
                conversation.id(),
                conversation.userId(),
                conversation.title(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
        
        try {
            conversationRepository.save(doc).block();
            System.out.println("[MongoDB] Conversation saved successfully");
        } catch (Exception e) {
            System.err.println("[MongoDB] Error saving conversation: " + e.getMessage());
            e.printStackTrace();
        }
        
        return conversation;
    }

    @Override
    public Optional<Conversation> findConversationByIdAndUserId(String conversationId, String userId) {
        try {
            ConversationDocument doc = conversationRepository.findByIdAndUserId(conversationId, userId).block();
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
        } catch (Exception e) {
            System.err.println("[MongoDB] Error finding conversation for user: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Conversation> findConversationById(String conversationId) {
        System.out.println("[MongoDB] Finding conversation - ID: " + conversationId);
        
        try {
            ConversationDocument doc = conversationRepository.findById(conversationId).block();
            if (doc == null) {
                System.out.println("[MongoDB] Conversation not found: " + conversationId);
                return Optional.empty();
            }
            
            System.out.println("[MongoDB] Conversation found: " + conversationId);
            return Optional.of(new Conversation(
                    doc.id(),
                    doc.userId(),
                    doc.title(),
                    doc.createdAt(),
                    doc.updatedAt()
            ));
        } catch (Exception e) {
            System.err.println("[MongoDB] Error finding conversation: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
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