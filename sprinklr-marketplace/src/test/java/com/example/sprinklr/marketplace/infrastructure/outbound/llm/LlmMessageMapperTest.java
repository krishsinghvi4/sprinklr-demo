package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.model.ToolResult;
import com.example.sprinklr.marketplace.infrastructure.config.LlmSystemPromptLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmMessageMapperTest {

    private static final Instant NOW = Instant.parse("2026-06-17T12:00:00Z");

    private LlmMessageMapper mapper;

    @BeforeEach
    void setUp() {
        LlmSystemPromptLoader promptLoader = mock(LlmSystemPromptLoader.class);
        when(promptLoader.getSystemPrompt()).thenReturn("system-prompt");
        mapper = new LlmMessageMapper(promptLoader);
    }

    @Test
    void turnScopedMapping_excludesPriorTurnToolRows_butIncludesCurrentTurnToolRows() {
        Message priorUser = userMessage("prior-user", "Show my tickets");
        Message priorAssistantTool = assistantToolCallMessage("prior-assistant-tool", "jira.search");
        Message priorTool = toolMessage("prior-tool", "prior-call", "{\"issues\":[]}");
        Message priorAssistantAnswer = assistantTextMessage("prior-assistant-answer", "Tickets: PROJ-1");

        Message currentUser = userMessage("current-user", "What about PROJ-2?");
        Message currentAssistantTool = assistantToolCallMessage("current-assistant-tool", "jira.fetch");
        Message currentTool = toolMessage("current-tool", "current-call", "{\"key\":\"PROJ-2\"}");

        List<Message> history = List.of(
                priorUser,
                priorAssistantTool,
                priorTool,
                priorAssistantAnswer,
                currentUser,
                currentAssistantTool,
                currentTool
        );

        List<LlmApiMessage> apiMessages = mapper.toTurnScopedApiMessages(
                history,
                "current-user",
                true,
                null
        );

        // system + 2 prior conversational + 3 current full agentic (user, assistant+tools, tool)
        assertEquals(6, apiMessages.size());
        assertEquals("system", apiMessages.get(0).role());
        assertEquals("user", apiMessages.get(1).role());
        assertEquals("Show my tickets", apiMessages.get(1).content());
        assertEquals("assistant", apiMessages.get(2).role());
        assertEquals("Tickets: PROJ-1", apiMessages.get(2).content());
        assertEquals("user", apiMessages.get(3).role());
        assertEquals("What about PROJ-2?", apiMessages.get(3).content());
        assertEquals("assistant", apiMessages.get(4).role());
        assertTrue(apiMessages.get(4).toolCalls() != null && !apiMessages.get(4).toolCalls().isEmpty());
        assertEquals("tool", apiMessages.get(5).role());
        assertEquals("{\"key\":\"PROJ-2\"}", apiMessages.get(5).content());
    }

    @Test
    void turnScopedMapping_currentTurnWithoutTools_staysConversational() {
        Message priorUser = userMessage("prior-user", "Hello");
        Message priorAnswer = assistantTextMessage("prior-answer", "Hi there");
        Message currentUser = userMessage("current-user", "Follow up");

        List<Message> history = List.of(priorUser, priorAnswer, currentUser);

        List<LlmApiMessage> apiMessages = mapper.toTurnScopedApiMessages(
                history,
                "current-user",
                false,
                null
        );

        assertEquals(4, apiMessages.size());
        assertEquals("user", apiMessages.get(1).role());
        assertEquals("assistant", apiMessages.get(2).role());
        assertEquals("user", apiMessages.get(3).role());
        assertEquals("Follow up", apiMessages.get(3).content());
    }

    @Test
    void countTurnScopedMappedHistoryMessages_matchesTurnScopedOutput() {
        Message priorUser = userMessage("prior-user", "Q1");
        Message priorTool = toolMessage("prior-tool", "call-1", "{\"big\":true}");
        Message priorAnswer = assistantTextMessage("prior-answer", "A1");
        Message currentUser = userMessage("current-user", "Q2");
        Message currentTool = toolMessage("current-tool", "call-2", "{\"big\":true}");

        List<Message> history = List.of(priorUser, priorTool, priorAnswer, currentUser, currentTool);

        int mappedCount = mapper.countTurnScopedMappedHistoryMessages(history, "current-user", true);
        List<LlmApiMessage> apiMessages = mapper.toTurnScopedApiMessages(history, "current-user", true, null);

        assertEquals(apiMessages.size() - 1, mappedCount);
    }

    private static Message userMessage(String id, String content) {
        return new Message(id, "conv-1", MessageRole.USER, content, List.of(), List.of(), NOW);
    }

    private static Message assistantTextMessage(String id, String content) {
        return new Message(id, "conv-1", MessageRole.ASSISTANT, content, List.of(), List.of(), NOW);
    }

    private static Message assistantToolCallMessage(String id, String toolName) {
        return new Message(
                id,
                "conv-1",
                MessageRole.ASSISTANT,
                null,
                List.of(new ToolCall("call-" + id, toolName, "{}")),
                List.of(),
                NOW
        );
    }

    private static Message toolMessage(String id, String toolCallId, String content) {
        return new Message(
                id,
                "conv-1",
                MessageRole.TOOL,
                null,
                List.of(),
                List.of(new ToolResult(toolCallId, content)),
                NOW
        );
    }
}
