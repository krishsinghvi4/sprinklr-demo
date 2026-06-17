package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoChatHistoryAdapterTurnTest {

    private static final Instant T1 = Instant.parse("2026-06-17T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-17T10:01:00Z");
    private static final Instant T3 = Instant.parse("2026-06-17T10:02:00Z");
    private static final Instant T4 = Instant.parse("2026-06-17T10:03:00Z");
    private static final Instant T5 = Instant.parse("2026-06-17T10:04:00Z");
    private static final Instant T6 = Instant.parse("2026-06-17T10:05:00Z");
    private static final Instant T7 = Instant.parse("2026-06-17T10:06:00Z");
    private static final Instant T8 = Instant.parse("2026-06-17T10:07:00Z");

    private ConversationRepository conversationRepository;
    private MongoMessageRepository messageRepository;
    private MongoChatHistoryAdapter adapter;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MongoMessageRepository.class);
        adapter = new MongoChatHistoryAdapter(conversationRepository, messageRepository);
    }

    @Test
    void findRecentTurns_returnsMessagesAcrossRequestedUserTurns() {
        String conversationId = "conv-1";

        MessageDocument turn1User = doc("u1", conversationId, MessageRole.USER, "Q1", T1);
        MessageDocument turn1Assistant = doc("a1", conversationId, MessageRole.ASSISTANT, "A1", T2);
        MessageDocument turn2User = doc("u2", conversationId, MessageRole.USER, "Q2", T3);
        MessageDocument turn2ToolAssistant = new MessageDocument(
                "a2",
                conversationId,
                MessageRole.ASSISTANT,
                null,
                List.of(new MessageDocument.ToolCallDocument("call-2", "jira.search", "{}")),
                List.of(),
                T4
        );
        MessageDocument turn2Tool = toolDoc("t2", conversationId, T5);
        MessageDocument turn2Answer = doc("a3", conversationId, MessageRole.ASSISTANT, "A2", T6);

        when(messageRepository.findByConversationIdAndRoleOrderByCreatedAtDesc(conversationId, MessageRole.USER))
                .thenReturn(Flux.just(turn2User, turn1User));
        when(messageRepository.findByConversationIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                eq(conversationId), eq(T1)))
                .thenReturn(Flux.just(
                        turn1User,
                        turn1Assistant,
                        turn2User,
                        turn2ToolAssistant,
                        turn2Tool,
                        turn2Answer
                ));

        List<Message> messages = adapter.findRecentTurns(conversationId, 2);

        assertEquals(6, messages.size());
        assertEquals(MessageRole.USER, messages.get(0).role());
        assertEquals("Q1", messages.get(0).content());
        assertEquals(MessageRole.ASSISTANT, messages.get(5).role());
        assertEquals("A2", messages.get(5).content());
    }

    @Test
    void findRecentTurns_withZeroLimit_returnsEmptyList() {
        assertTrue(adapter.findRecentTurns("conv-1", 0).isEmpty());
    }

    @Test
    void truncateToolResults_persistsStubText() {
        MessageDocument.ToolResultDocument original = new MessageDocument.ToolResultDocument("call-1", "{\"huge\":true}");
        MessageDocument existing = new MessageDocument(
                "tool-1",
                "conv-1",
                MessageRole.TOOL,
                null,
                List.of(),
                List.of(original),
                T8
        );

        when(messageRepository.findById("tool-1")).thenReturn(Mono.just(existing));
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(invocation -> {
            MessageDocument saved = invocation.getArgument(0);
            assertEquals(MongoChatHistoryAdapter.TOOL_RESULT_TRUNCATED_STUB,
                    saved.toolResults().get(0).content());
            return Mono.just(saved);
        });

        adapter.truncateToolResults("tool-1");
    }

    private static MessageDocument doc(
            String id,
            String conversationId,
            MessageRole role,
            String content,
            Instant createdAt
    ) {
        return new MessageDocument(id, conversationId, role, content, List.of(), List.of(), createdAt);
    }

    private static MessageDocument toolDoc(String id, String conversationId, Instant createdAt) {
        return new MessageDocument(
                id,
                conversationId,
                MessageRole.TOOL,
                null,
                List.of(),
                List.of(new MessageDocument.ToolResultDocument("call-2", "{\"issues\":[]}")),
                createdAt
        );
    }
}
