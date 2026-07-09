package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.adapters;

import com.example.sprinklr.marketplace.domain.model.chat.Conversation;
import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.example.sprinklr.marketplace.domain.model.chat.MessageRole;
import com.example.sprinklr.marketplace.domain.model.tool.ToolCall;
import com.example.sprinklr.marketplace.domain.model.tool.ToolResult;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.MongoMessageRepository;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.ConversationDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.MessageDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.ConversationRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class MongoChatHistoryAdapter implements ChatHistoryPort {

    private static final int PREVIEW_MAX_LENGTH = 80;
    private static final int TOOL_SNAPSHOT_MAX_CHARS = 12_000;
    public static final String TOOL_RESULT_TRUNCATED_STUB = "[Tool result truncated after summarization]";

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
    public List<Message> findRecentTurns(String conversationId, int turnLimit) {
        if (turnLimit <= 0) {
            return List.of();
        }

        System.out.println("[MongoDB] Finding recent turns for conversation: " + conversationId + " (turnLimit: " + turnLimit + ")");

        try {
            List<MessageDocument> userMessages = messageRepository
                    .findByConversationIdAndRoleOrderByCreatedAtDesc(conversationId, MessageRole.USER)
                    .take(turnLimit)
                    .collectList()
                    .block();

            if (userMessages == null || userMessages.isEmpty()) {
                System.out.println("[MongoDB] No user turns found for conversation: " + conversationId);
                return List.of();
            }

            Collections.reverse(userMessages);
            Instant cutoff = userMessages.get(0).createdAt();

            List<MessageDocument> documents = messageRepository
                    .findByConversationIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(conversationId, cutoff)
                    .collectList()
                    .block();

            if (documents == null || documents.isEmpty()) {
                return List.of();
            }

            List<Message> result = documents.stream()
                    .map(this::toDomainMessage)
                    .toList();

            System.out.println("[MongoDB] Found " + result.size() + " messages across " + userMessages.size() + " turns");
            return result;
        } catch (Exception e) {
            System.err.println("[MongoDB] Error finding turns for conversation " + conversationId + ": " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public void truncateToolResults(String messageId) {
        System.out.println("[MongoDB] Truncating tool results for message: " + messageId);

        try {
            MessageDocument existing = messageRepository.findById(messageId).block();
            if (existing == null || existing.role() != MessageRole.TOOL) {
                return;
            }

            List<MessageDocument.ToolResultDocument> truncatedResults = existing.toolResults().stream()
                    .map(result -> new MessageDocument.ToolResultDocument(
                            result.toolCallId(),
                            TOOL_RESULT_TRUNCATED_STUB
                    ))
                    .toList();

            MessageDocument updated = new MessageDocument(
                    existing.id(),
                    existing.conversationId(),
                    existing.role(),
                    existing.content(),
                    existing.toolCalls(),
                    truncatedResults,
                    existing.createdAt()
            );
            messageRepository.save(updated).block();
            System.out.println("[MongoDB] Tool results truncated for message: " + messageId);
        } catch (Exception e) {
            System.err.println("[MongoDB] Error truncating tool results for message " + messageId + ": " + e.getMessage());
            e.printStackTrace();
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

    @Override
    public List<Conversation> findConversationsByUserId(String userId) {
        try {
            List<ConversationDocument> documents = conversationRepository
                    .findByUserIdOrderByUpdatedAtDesc(userId)
                    .collectList()
                    .block();

            if (documents == null || documents.isEmpty()) {
                return List.of();
            }

            return documents.stream()
                    .map(doc -> new Conversation(
                            doc.id(),
                            doc.userId(),
                            doc.title(),
                            doc.createdAt(),
                            doc.updatedAt()
                    ))
                    .toList();
        } catch (Exception e) {
            System.err.println("[MongoDB] Error finding conversations for user: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<String> findFirstUserMessageContent(String conversationId) {
        try {
            MessageDocument document = messageRepository
                    .findFirstByConversationIdAndRoleOrderByCreatedAtAsc(conversationId, MessageRole.USER)
                    .block();
            if (document == null || document.content() == null || document.content().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(document.content());
        } catch (Exception e) {
            System.err.println("[MongoDB] Error finding first user message: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void touchConversation(String conversationId, String preview) {
        try {
            ConversationDocument existing = conversationRepository.findById(conversationId).block();
            if (existing == null) {
                return;
            }

            String title = existing.title();
            if ((title == null || title.isBlank()) && preview != null && !preview.isBlank()) {
                title = truncatePreview(preview);
            }

            ConversationDocument updated = new ConversationDocument(
                    existing.id(),
                    existing.userId(),
                    title,
                    existing.createdAt(),
                    Instant.now()
            );
            conversationRepository.save(updated).block();
        } catch (Exception e) {
            System.err.println("[MongoDB] Error touching conversation: " + e.getMessage());
        }
    }

    @Override
    public void deleteConversation(String conversationId, String userId) {
        if (findConversationByIdAndUserId(conversationId, userId).isEmpty()) {
            return;
        }
        messageRepository.deleteByConversationId(conversationId).block();
        conversationRepository.deleteByIdAndUserId(conversationId, userId).block();
    }

    @Override
    public Optional<String> collectToolResultSnapshotForAssistantMessage(
            String conversationId,
            String assistantMessageId
    ) {
        if (conversationId == null || conversationId.isBlank()
                || assistantMessageId == null || assistantMessageId.isBlank()) {
            return Optional.empty();
        }
        try {
            MessageDocument assistant = messageRepository.findById(assistantMessageId).block();
            if (assistant == null || !conversationId.equals(assistant.conversationId())) {
                return Optional.empty();
            }

            Instant turnStart = messageRepository
                    .findByConversationIdAndRoleOrderByCreatedAtDesc(conversationId, MessageRole.USER)
                    .filter(user -> user.createdAt().isBefore(assistant.createdAt()))
                    .next()
                    .map(MessageDocument::createdAt)
                    .blockOptional()
                    .orElse(assistant.createdAt());

            List<MessageDocument> turnMessages = messageRepository
                    .findByConversationIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(conversationId, turnStart)
                    .filter(doc -> !doc.createdAt().isAfter(assistant.createdAt()))
                    .collectList()
                    .blockOptional()
                    .orElse(List.of());

            StringBuilder snapshot = new StringBuilder();
            for (MessageDocument doc : turnMessages) {
                if (doc.role() != MessageRole.TOOL || doc.toolResults() == null) {
                    continue;
                }
                for (MessageDocument.ToolResultDocument result : doc.toolResults()) {
                    if (result.content() == null || result.content().isBlank()
                            || TOOL_RESULT_TRUNCATED_STUB.equals(result.content())) {
                        continue;
                    }
                    if (snapshot.length() > 0) {
                        snapshot.append("\n\n---\n\n");
                    }
                    snapshot.append(result.content());
                    if (snapshot.length() >= TOOL_SNAPSHOT_MAX_CHARS) {
                        return Optional.of(snapshot.substring(0, TOOL_SNAPSHOT_MAX_CHARS) + "\n…(truncated)");
                    }
                }
            }
            return snapshot.isEmpty() ? Optional.empty() : Optional.of(snapshot.toString());
        } catch (Exception exception) {
            System.err.println("[MongoDB] Error collecting tool snapshot for message "
                    + assistantMessageId + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static String truncatePreview(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_MAX_LENGTH - 3) + "...";
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