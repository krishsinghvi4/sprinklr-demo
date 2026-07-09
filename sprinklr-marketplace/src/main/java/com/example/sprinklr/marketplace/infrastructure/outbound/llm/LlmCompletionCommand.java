package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.domain.model.chat.Message;

import java.util.List;

/**
 * Input to {@link LlmService#complete(LlmCompletionCommand)}.
 * <p>
 * Keeps HTTP/service-layer types separate from the domain {@link LlmRequest}
 * so the service can evolve (e.g. add tracing IDs) without changing the domain port.
 */
public record LlmCompletionCommand(
        List<Message> history,
        List<McpTool> tools,
        /**
         * When non-null, prior turns use conversational mapping; the current turn (from this USER message onward)
         * uses full agentic mapping when {@code fullToolHistoryForCurrentTurn} is true.
         */
        String currentTurnUserMessageId,
        boolean fullToolHistoryForCurrentTurn,
        /**
         * When non-null, replaces the default system prompt (e.g. post-tool summary pass).
         */
        String systemPromptOverride,
        /**
         * When present, LLM token usage is recorded for the user after a successful completion.
         */
        LlmUsageContext usageContext
) {
    public LlmCompletionCommand(List<Message> history, List<McpTool> tools, boolean includeFullToolHistory) {
        this(history, tools, null, includeFullToolHistory, null, null);
    }

    public LlmCompletionCommand(
            List<Message> history,
            List<McpTool> tools,
            String currentTurnUserMessageId,
            boolean fullToolHistoryForCurrentTurn,
            String systemPromptOverride
    ) {
        this(history, tools, currentTurnUserMessageId, fullToolHistoryForCurrentTurn, systemPromptOverride, null);
    }
}
