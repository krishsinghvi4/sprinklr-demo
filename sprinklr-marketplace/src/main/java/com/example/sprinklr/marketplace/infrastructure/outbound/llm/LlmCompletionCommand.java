package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;

import java.util.List;

/**
 * Input to {@link LlmService#complete(LlmCompletionCommand)}.
 * <p>
 * Keeps HTTP/service-layer types separate from the domain {@link com.example.sprinklr.marketplace.domain.model.LlmRequest}
 * so the service can evolve (e.g. add tracing IDs) without changing the domain port.
 */
public record LlmCompletionCommand(
        List<Message> history,
        List<McpTool> tools,
        /**
         * When true, map full agentic history (USER, ASSISTANT with tool_calls, TOOL results).
         * When false, map conversational history only (USER + ASSISTANT text) for text-only turns.
         */
        boolean includeFullToolHistory
) {
}
