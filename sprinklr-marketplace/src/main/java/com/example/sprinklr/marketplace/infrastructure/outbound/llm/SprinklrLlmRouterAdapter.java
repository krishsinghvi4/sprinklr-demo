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

    private static final String CONNECTED_TOOLS_NUDGE = """

            ## Mandatory tool use (this request only)
            The MCP tools array in this request is non-empty: the user's integrations ARE connected.
            Do NOT say Jira, GitLab, Teams, or Red is disconnected or ask the user to connect via Profile.
            Call the appropriate MCP tool(s) now to answer the live-data request.""";

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

        if (LlmToolUseRetryPolicy.shouldRetryForToolUse(
                request.tools().size(),
                result.toolCalls().isEmpty(),
                request.prompt(),
                result.content()
        )) {
            log.info("[LLM] Model returned text-only despite {} tools; retrying with connected-tools nudge",
                    request.tools().size());

            result = llmService.complete(new LlmCompletionCommand(
                    request.history(),
                    request.tools(),
                    request.currentTurnUserMessageId(),
                    fullToolHistoryForCurrentTurn,
                    systemPromptLoader.getSystemPrompt() + CONNECTED_TOOLS_NUDGE
            ));
        }

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
