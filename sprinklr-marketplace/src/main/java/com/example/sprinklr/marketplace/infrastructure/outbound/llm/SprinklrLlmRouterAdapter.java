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
 * Hexagonal adapter that bridges the domain LLM port to the internal LLM service.
 * Keeps HTTP and JSON logic out of domain-facing code.
 */
public class SprinklrLlmRouterAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(SprinklrLlmRouterAdapter.class);

    private final LlmService llmService;
    private final LlmSystemPromptLoader systemPromptLoader;

    public SprinklrLlmRouterAdapter(LlmService llmService, LlmSystemPromptLoader systemPromptLoader) {
        this.llmService = llmService;
        this.systemPromptLoader = systemPromptLoader;
    }

    /**
     * Executes a single completion to decide between text and tool calls.
     */
    @Override
    public LlmResponse complete(LlmRequest request) {
        boolean fullToolHistoryForCurrentTurn = !request.tools().isEmpty();

        LlmCompletionResult result = llmService.complete(new LlmCompletionCommand(
                request.history(),
                request.tools(),
                request.currentTurnUserMessageId(),
                fullToolHistoryForCurrentTurn,
                null
        ));

        return new LlmResponse(result.content(), result.toolCalls());
    }

    /**
     * Runs a summary pass after tool execution using a summary prompt.
     */
    @Override
    public void streamSummary(LlmRequest request, Flow.Subscriber<String> subscriber) {
        log.info("[LLM] streamSummary() historySize={}", request.history().size());

        LlmCompletionResult result = llmService.complete(new LlmCompletionCommand(
                request.history(),
                List.of(),
                request.currentTurnUserMessageId(),
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
