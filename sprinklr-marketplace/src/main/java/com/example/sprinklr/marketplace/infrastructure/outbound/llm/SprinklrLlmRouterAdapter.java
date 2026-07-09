package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.example.sprinklr.marketplace.domain.model.chat.MessageRole;
import com.example.sprinklr.marketplace.domain.port.outbound.LLM.LlmPort;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmSystemPromptLoader;
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
    private final McpSkillPromptAssembler skillPromptAssembler;

    public SprinklrLlmRouterAdapter(
            LlmService llmService,
            LlmSystemPromptLoader systemPromptLoader,
            McpSkillPromptAssembler skillPromptAssembler
    ) {
        this.llmService = llmService;
        this.systemPromptLoader = systemPromptLoader;
        this.skillPromptAssembler = skillPromptAssembler;
    }

    /**
     * Executes a single completion to decide between text and tool calls.
     */
    @Override
    public LlmResponse complete(LlmRequest request) {
        boolean fullToolHistoryForCurrentTurn = !request.tools().isEmpty();
        String systemPrompt = skillPromptAssembler.assemble(
                systemPromptLoader.getSystemPrompt(),
                request.tools(),
                request.userId());
        systemPrompt = LlmCurrentDateContext.append(systemPrompt);
        if (request.additionalContext() != null && !request.additionalContext().isBlank()) {
            // Continuation context: prior-turn tool results reused so the agent need not re-fetch them.
            systemPrompt = systemPrompt + "\n\n## Continuation context\n" + request.additionalContext();
        }

        LlmCompletionResult result = llmService.complete(new LlmCompletionCommand(
                request.history(),
                request.tools(),
                request.currentTurnUserMessageId(),
                fullToolHistoryForCurrentTurn,
                systemPrompt,
                usageContext(request)
        ));

        boolean toolResultsAlreadyInTurn = hasToolResultsInCurrentTurn(request);
        if (LlmToolUseRetryPolicy.shouldRetryForToolUse(
                request.tools().size(),
                result.toolCalls().isEmpty(),
                request.prompt(),
                result.content(),
                toolResultsAlreadyInTurn
        )) {
            LlmToolUseRetryPolicy.RetryReason retryReason = LlmToolUseRetryPolicy.retryReasonForToolUse(
                    request.tools().size(),
                    result.toolCalls().isEmpty(),
                    request.prompt(),
                    result.content(),
                    toolResultsAlreadyInTurn
            ).orElse(null);
            log.info("[LLM] Model returned text-only before any tool use despite {} tools; retrying (reason={})",
                    request.tools().size(), retryReason);

            result = llmService.complete(new LlmCompletionCommand(
                    request.history(),
                    request.tools(),
                    request.currentTurnUserMessageId(),
                    fullToolHistoryForCurrentTurn,
                    systemPrompt + CONNECTED_TOOLS_NUDGE,
                    usageContext(request)
            ));
        }

        return new LlmResponse(result.content(), result.toolCalls());
    }

    private static LlmUsageContext usageContext(LlmRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            return null;
        }
        return new LlmUsageContext(request.userId(), request.conversationId());
    }

    /**
     * Once tool results exist in the current turn, text-only is expected (agentic loop exit).
     */
    private static boolean hasToolResultsInCurrentTurn(LlmRequest request) {
        if (request.currentTurnUserMessageId() == null || request.currentTurnUserMessageId().isBlank()) {
            return false;
        }
        boolean pastCurrentUserMessage = false;
        for (Message message : request.history()) {
            if (request.currentTurnUserMessageId().equals(message.id())) {
                pastCurrentUserMessage = true;
                continue;
            }
            if (pastCurrentUserMessage && message.role() == MessageRole.TOOL) {
                return true;
            }
        }
        return false;
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
