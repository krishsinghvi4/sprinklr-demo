package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.ToolResult;
import com.example.sprinklr.marketplace.infrastructure.config.LlmSystemPromptLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiMessage;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiToolCall;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts domain {@link Message} history into the router's {@code messages} JSON array.
 * <p>
 * The orchestrator always passes conversation history (last {@code HISTORY_LIMIT} messages from MongoDB
 * plus the new user message). This mapper decides <em>how</em> that history is shaped for the LLM:
 * <ul>
 *   <li><b>Text-only / decision pass (no tools):</b> USER and ASSISTANT messages with text content.
 *       Tool-only rows are skipped so the model sees a clean dialogue.</li>
 *   <li><b>Agentic / MCP path:</b> Full history including ASSISTANT tool_call rows and TOOL result rows
 *       so the model can reason over prior tool invocations in the same conversation.</li>
 * </ul>
 * The latest user prompt is already the last entry in {@code history} — we never duplicate
 * {@link com.example.sprinklr.marketplace.domain.model.LlmRequest#prompt()} here.
 */
@Component
public class LlmMessageMapper {

    private final LlmSystemPromptLoader systemPromptLoader;

    public LlmMessageMapper(LlmSystemPromptLoader systemPromptLoader) {
        this.systemPromptLoader = systemPromptLoader;
    }

    /**
     * Builds the full {@code messages} array: system prompt first, then history entries.
     *
     * @param history                 conversation messages (includes current user message)
     * @param includeFullToolHistory  true when MCP tools are active or summarizing after tools
     */
    public List<LlmApiMessage> toApiMessages(List<Message> history, boolean includeFullToolHistory) {
        return toApiMessages(history, includeFullToolHistory, null);
    }

    public List<LlmApiMessage> toApiMessages(
            List<Message> history,
            boolean includeFullToolHistory,
            String systemPromptOverride
    ) {
        List<LlmApiMessage> messages = new ArrayList<>();

        String systemPrompt = systemPromptOverride != null && !systemPromptOverride.isBlank()
                ? systemPromptOverride
                : systemPromptLoader.getSystemPrompt();
        messages.add(new LlmApiMessage("system", systemPrompt, null, null));

        int mappedCount = 0;
        for (Message message : history) {
            List<LlmApiMessage> mapped = mapMessage(message, includeFullToolHistory);
            if (!mapped.isEmpty()) {
                messages.addAll(mapped);
                mappedCount += mapped.size();
            }
        }

        return messages;
    }

    /**
     * Returns how many history rows were converted (for logging — compare to raw history size).
     */
    public int countMappedHistoryMessages(List<Message> history, boolean includeFullToolHistory) {
        int count = 0;
        for (Message message : history) {
            count += mapMessage(message, includeFullToolHistory).size();
        }
        return count;
    }

    private List<LlmApiMessage> mapMessage(Message message, boolean includeFullToolHistory) {
        if (includeFullToolHistory) {
            return mapFullHistoryMessage(message);
        }
        return mapConversationalMessage(message);
    }

    /**
     * Conversational mode: only USER and ASSISTANT rows with readable text.
     * Used on the first LLM pass when deciding text vs tools (even if the user mentions Jira/GitLab).
     */
    private List<LlmApiMessage> mapConversationalMessage(Message message) {
        if (message.role() == MessageRole.USER && hasText(message.content())) {
            return List.of(new LlmApiMessage("user", message.content(), null, null));
        }
        if (message.role() == MessageRole.ASSISTANT && hasText(message.content())) {
            return List.of(new LlmApiMessage("assistant", message.content(), null, null));
        }
        // Skip TOOL rows and assistant rows that only contain tool_calls (no summary text yet).
        return List.of();
    }

    /**
     * Full agentic mode: replay tool calls and tool results so the LLM has full context
     * when tools are enabled or when synthesizing a summary after MCP execution.
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
                    // content may be null when the assistant only requested tools
                    return List.of(new LlmApiMessage("assistant", message.content(), apiToolCalls, null));
                }
                if (hasText(message.content())) {
                    return List.of(new LlmApiMessage("assistant", message.content(), null, null));
                }
                return List.of();

            case TOOL:
                // Each tool result becomes its own API message linked by tool_call_id.
                List<LlmApiMessage> toolMessages = new ArrayList<>();
                for (ToolResult result : message.toolResults()) {
                    toolMessages.add(new LlmApiMessage("tool", result.content(), null, result.toolCallId()));
                }
                return toolMessages;

            case SYSTEM:
                // System rows from DB are rare; skip to avoid overriding our configured system prompt.
                return List.of();

            default:
                return List.of();
        }
    }

    private static boolean hasText(String content) {
        return content != null && !content.isBlank();
    }
}
