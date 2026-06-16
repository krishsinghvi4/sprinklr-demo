package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import com.example.sprinklr.marketplace.infrastructure.config.LlmSystemPromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    private final LlmSystemPromptLoader systemPromptLoader;

    public SprinklrLlmRouterAdapter(LlmService llmService, LlmSystemPromptLoader systemPromptLoader) {
        this.llmService = llmService;
        this.systemPromptLoader = systemPromptLoader;
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
        log.info("[LLM] streamSummary() historySize={}", request.history().size());

        LlmCompletionResult result = llmService.complete(new LlmCompletionCommand(
                request.history(),
                List.of(),
                true,
                systemPromptLoader.getSummaryPrompt()
        ));

        String content = result.content() != null ? result.content() : "";
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (!content.isEmpty()) {
                    subscriber.onNext(content);
                }
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
            }
        });
    }
}
