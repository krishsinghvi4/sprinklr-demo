package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.example.sprinklr.marketplace.domain.model.chat.MessageRole;
import com.example.sprinklr.marketplace.domain.model.tool.ToolResult;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmSystemPromptLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiMessage;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiToolCall;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts domain {@link Message} history into the router's {@code messages} JSON array.
 * <p>
 * Supports three mapping strategies:
 * <ul>
 *   <li><b>Global conversational:</b> USER and ASSISTANT text only (legacy / no turn boundary).</li>
 *   <li><b>Global full agentic:</b> all tool_calls and TOOL rows included.</li>
 *   <li><b>Turn-scoped:</b> prior turns conversational; current turn full agentic when tools are active.</li>
 * </ul>
 * The latest user prompt is already the last entry in {@code history} — we never duplicate
 * {@link LlmRequest#prompt()} here.
 */
@Component
public class LlmMessageMapper {

    private final LlmSystemPromptLoader systemPromptLoader;

    public LlmMessageMapper(LlmSystemPromptLoader systemPromptLoader) {
        this.systemPromptLoader = systemPromptLoader;
    }

    /**
     * Builds the message list using the default system prompt (legacy global mapping mode).
     */
    public List<LlmApiMessage> toApiMessages(List<Message> history, boolean includeFullToolHistory) {
        return toApiMessages(history, includeFullToolHistory, null);
    }

    /**
     * Builds the message list with optional system prompt override (legacy global mapping mode).
     */
    public List<LlmApiMessage> toApiMessages(
            List<Message> history,
            boolean includeFullToolHistory,
            String systemPromptOverride
    ) {
        return toTurnScopedApiMessages(history, null, includeFullToolHistory, systemPromptOverride);
    }

    /**
     * Builds the message list with turn-scoped mapping when {@code currentTurnUserMessageId} is set.
     */
    public List<LlmApiMessage> toTurnScopedApiMessages(
            List<Message> history,
            String currentTurnUserMessageId,
            boolean fullToolHistoryForCurrentTurn,
            String systemPromptOverride
    ) {
        List<LlmApiMessage> messages = new ArrayList<>();

        String systemPrompt = systemPromptOverride != null && !systemPromptOverride.isBlank()
                ? systemPromptOverride
                : systemPromptLoader.getSystemPrompt();
        messages.add(new LlmApiMessage("system", systemPrompt, null, null));

        for (Message message : history) {
            boolean includeFullToolHistory = resolveIncludeFullToolHistory(
                    message,
                    history,
                    currentTurnUserMessageId,
                    fullToolHistoryForCurrentTurn
            );
            List<LlmApiMessage> mapped = mapMessage(message, includeFullToolHistory);
            if (!mapped.isEmpty()) {
                messages.addAll(mapped);
            }
        }

        return messages;
    }

    /**
     * Returns how many history rows were converted (for logging — compare to raw history size).
     */
    public int countMappedHistoryMessages(List<Message> history, boolean includeFullToolHistory) {
        return countTurnScopedMappedHistoryMessages(history, null, includeFullToolHistory);
    }

    public int countTurnScopedMappedHistoryMessages(
            List<Message> history,
            String currentTurnUserMessageId,
            boolean fullToolHistoryForCurrentTurn
    ) {
        int count = 0;
        for (Message message : history) {
            boolean includeFullToolHistory = resolveIncludeFullToolHistory(
                    message,
                    history,
                    currentTurnUserMessageId,
                    fullToolHistoryForCurrentTurn
            );
            count += mapMessage(message, includeFullToolHistory).size();
        }
        return count;
    }

    private boolean resolveIncludeFullToolHistory(
            Message message,
            List<Message> history,
            String currentTurnUserMessageId,
            boolean fullToolHistoryForCurrentTurn
    ) {
        if (currentTurnUserMessageId == null || currentTurnUserMessageId.isBlank()) {
            return fullToolHistoryForCurrentTurn;
        }
        if (!isCurrentTurnMessage(message, history, currentTurnUserMessageId)) {
            return false;
        }
        return fullToolHistoryForCurrentTurn;
    }

    private boolean isCurrentTurnMessage(Message message, List<Message> history, String currentTurnUserMessageId) {
        int turnStartIndex = -1;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).id().equals(currentTurnUserMessageId)) {
                turnStartIndex = i;
                break;
            }
        }
        if (turnStartIndex < 0) {
            return false;
        }
        for (int i = turnStartIndex; i < history.size(); i++) {
            if (history.get(i).id().equals(message.id())) {
                return true;
            }
        }
        return false;
    }

    private List<LlmApiMessage> mapMessage(Message message, boolean includeFullToolHistory) {
        if (includeFullToolHistory) {
            return mapFullHistoryMessage(message);
        }
        return mapConversationalMessage(message);
    }

    /**
     * Conversational mode: only USER and ASSISTANT rows with readable text.
     */
    private List<LlmApiMessage> mapConversationalMessage(Message message) {
        if (message.role() == MessageRole.USER && hasText(message.content())) {
            return List.of(new LlmApiMessage("user", message.content(), null, null));
        }
        if (message.role() == MessageRole.ASSISTANT && hasText(message.content())) {
            return List.of(new LlmApiMessage("assistant", message.content(), null, null));
        }
        return List.of();
    }

    /**
     * Full agentic mode: replay tool calls and tool results for the current turn.
     */
    private List<LlmApiMessage> mapFullHistoryMessage(Message message) {
        switch (message.role()) {
            case USER:
                if (hasText(message.content())) {
                    return List.of(new LlmApiMessage("user", message.content(), null, null));
                }
                return List.of();

            case ASSISTANT:
                if (!message.toolCalls().isEmpty()) {
                    List<LlmApiToolCall> apiToolCalls = message.toolCalls().stream()
                            .map(tc -> new LlmApiToolCall(
                                    tc.id(),
                                    "function",
                                    new LlmApiToolCall.LlmApiToolCallFunction(tc.name(), tc.argumentsJson())
                            ))
                            .toList();
                    return List.of(new LlmApiMessage("assistant", message.content(), apiToolCalls, null));
                }
                if (hasText(message.content())) {
                    return List.of(new LlmApiMessage("assistant", message.content(), null, null));
                }
                return List.of();

            case TOOL:
                List<LlmApiMessage> toolMessages = new ArrayList<>();
                for (ToolResult result : message.toolResults()) {
                    toolMessages.add(new LlmApiMessage("tool", result.content(), null, result.toolCallId()));
                }
                return toolMessages;

            case SYSTEM:
                return List.of();

            default:
                return List.of();
        }
    }

    private static boolean hasText(String content) {
        return content != null && !content.isBlank();
    }
}
