package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Flow;

/**
 * Hexagonal adapter: maps domain {@link LlmRequest} to {@link LlmService} and back to {@link LlmResponse}.
 * <p>
 * This class intentionally contains no HTTP or JSON logic — it only translates between
 * domain types and the internal service layer.
 */
public class SprinklrLlmRouterAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(SprinklrLlmRouterAdapter.class);

    private final LlmService llmService;

    public SprinklrLlmRouterAdapter(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        // When MCP tools are whitelisted, send full agentic history (tool_calls + tool results).
        // For text-only turns, still send conversational history (USER + ASSISTANT text) — never an empty history.
        boolean includeFullToolHistory = !request.tools().isEmpty();

        LlmCompletionResult result = llmService.complete(new LlmCompletionCommand(
                request.history(),
                request.tools(),
                includeFullToolHistory
        ));

        return new LlmResponse(result.content(), result.toolCalls());
    }

    @Override
    public void streamSummary(LlmRequest request, Flow.Subscriber<String> subscriber) {
        // Deferred until MCP streamSummary endpoint is wired; stub adapter supports tool-flow testing.
        log.warn("[LLM] streamSummary() not implemented on real adapter — MCP summarization pending");
        throw new UnsupportedOperationException(
                "streamSummary is not implemented until MCP summarization is wired");
    }
}
