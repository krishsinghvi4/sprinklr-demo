package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.application.service.tool.ToolSelectionService;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.MCP.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.MCP.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.domain.model.MCP.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.example.sprinklr.marketplace.domain.model.chat.MessageRole;
import com.example.sprinklr.marketplace.domain.model.tool.ToolCall;
import com.example.sprinklr.marketplace.domain.model.tool.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.model.tool.ToolResult;
import com.example.sprinklr.marketplace.domain.model.tool.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.domain.port.outbound.LLM.LlmPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight.UserPromptValueMatcher;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedQueryPreferencesToolDescriptionAugmenter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DashboardRegenerateService {

    private static final Logger log = LoggerFactory.getLogger(DashboardRegenerateService.class);
    private static final String TOOL_LIMIT_MESSAGE =
            "Tool call limit reached for this request. Please try a simpler question.";
    private static final String REGEN_CONTEXT_BASE = """
            Dashboard regeneration: re-run the user's prompt with fresh MCP tool data and produce \
            dashboard analytics.
            Follow system prompt widget rules. When tool data contains quantitative patterns \
            (changelog events, status durations, distributions, trends, comparisons), emit 1–3 \
            ```widget``` charts — this is a saved dashboard insight, not a casual chat reply.
            If the data truly cannot support any meaningful chart, respond with prose only \
            (no widget fence).""";

    private final LlmPort llmPort;
    private final McpServerPort mcpServerPort;
    private final McpRegistryPort mcpRegistryPort;
    private final McpProperties mcpProperties;
    private final McpInvocationPreflightPort mcpInvocationPreflightPort;
    private final InsightsDashboardService insightsDashboardService;
    private final ToolSelectionService toolSelectionService;
    private final RedQueryPreferencesToolDescriptionAugmenter redQueryPreferencesToolDescriptionAugmenter;

    public DashboardRegenerateService(
            LlmPort llmPort,
            McpServerPort mcpServerPort,
            McpRegistryPort mcpRegistryPort,
            McpProperties mcpProperties,
            McpInvocationPreflightPort mcpInvocationPreflightPort,
            InsightsDashboardService insightsDashboardService,
            ToolSelectionService toolSelectionService,
            RedQueryPreferencesToolDescriptionAugmenter redQueryPreferencesToolDescriptionAugmenter
    ) {
        this.llmPort = llmPort;
        this.mcpServerPort = mcpServerPort;
        this.mcpRegistryPort = mcpRegistryPort;
        this.mcpProperties = mcpProperties;
        this.mcpInvocationPreflightPort = mcpInvocationPreflightPort;
        this.insightsDashboardService = insightsDashboardService;
        this.toolSelectionService = toolSelectionService;
        this.redQueryPreferencesToolDescriptionAugmenter = redQueryPreferencesToolDescriptionAugmenter;
    }

    public void streamRegenerate(
            String userId,
            DashboardTurn existingTurn,
            String promptOverride,
            Flow.Subscriber<String> responseSubscriber
    ) {
        Mono.fromRunnable(() -> runRegenerateLoop(userId, existingTurn, promptOverride, responseSubscriber))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {},
                        error -> signalError(responseSubscriber, error)
                );
    }

    private void runRegenerateLoop(
            String userId,
            DashboardTurn existingTurn,
            String promptOverride,
            Flow.Subscriber<String> responseSubscriber
    ) {
        try {
            responseSubscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });

            String prompt = resolvePrompt(existingTurn.prompt(), promptOverride);

            List<Message> history = new ArrayList<>();
            String userMessageId = newId();
            Message userMessage = new Message(
                    userMessageId,
                    "dashboard-regen",
                    MessageRole.USER,
                    prompt,
                    List.of(),
                    List.of(),
                    Instant.now()
            );
            history.add(userMessage);

            List<McpTool> toolsForTurn = resolveToolsForRegenerate(userId, prompt, history);
            log.info("[DashboardRegenerate] turnId={} scopedTools={}", existingTurn.id(), toolsForTurn.size());
            String regenContext = buildRegenContext(existingTurn);

            int iteration = 0;
            int totalToolCalls = 0;
            AtomicReference<String> lastToolSnapshot = new AtomicReference<>(existingTurn.toolResultSnapshot());

            while (iteration < mcpProperties.getMaxAgenticIterations()) {
                if (iteration > 0) {
                    emitProgress(responseSubscriber, "Generating analytics…\n");
                }
                LlmResponse llmResponse = llmPort.complete(new LlmRequest(
                        prompt,
                        history,
                        toolsForTurn,
                        userMessageId,
                        userId,
                        "dashboard-regen-" + existingTurn.id(),
                        regenContext
                ));

                if (llmResponse.toolCalls().isEmpty()) {
                    String content = llmResponse.content() != null ? llmResponse.content() : "";
                    persistRegeneratedTurn(userId, existingTurn, prompt, content, lastToolSnapshot.get());
                    responseSubscriber.onComplete();
                    return;
                }

                if (totalToolCalls + llmResponse.toolCalls().size() > mcpProperties.getMaxToolCallsPerTurn()) {
                    responseSubscriber.onComplete();
                    return;
                }

                executeToolBatch(userId, history, llmResponse, lastToolSnapshot, userMessageId, responseSubscriber);
                totalToolCalls += llmResponse.toolCalls().size();
                iteration++;
            }

            responseSubscriber.onComplete();
        } catch (Exception exception) {
            signalError(responseSubscriber, exception);
        }
    }

    private static String resolvePrompt(String existingPrompt, String promptOverride) {
        if (promptOverride != null && !promptOverride.isBlank()) {
            return promptOverride.trim();
        }
        return existingPrompt;
    }

    private List<McpTool> resolveToolsForRegenerate(String userId, String prompt, List<Message> history) {
        List<McpTool> userTools = redQueryPreferencesToolDescriptionAugmenter.augment(
                userId,
                mcpRegistryPort.findActiveToolsForUser(userId));
        if (userTools.isEmpty()) {
            return List.of();
        }
        if (mcpProperties.getToolSelection().isEnabled()) {
            try {
                List<ToolDependencyGraph> graphs = mcpRegistryPort.findActiveDependencyGraphsForUser(userId);
                ToolSelectionResult selection = toolSelectionService.selectTools(
                        prompt, history, userTools, graphs, Optional.empty());
                if (!selection.scopedTools().isEmpty()) {
                    return selection.scopedTools();
                }
            } catch (Exception exception) {
                log.warn("[DashboardRegenerate] Tool selection failed: {}", exception.getMessage());
            }
        }
        return userTools;
    }

    private static String buildRegenContext(DashboardTurn existingTurn) {
        StringBuilder context = new StringBuilder(REGEN_CONTEXT_BASE);
        if (existingTurn.widgets() != null && !existingTurn.widgets().isEmpty()) {
            context.append("\nThis turn previously had analytics widgets — regenerate charts when ")
                    .append("the fresh tool data supports them.");
        }
        return context.toString();
    }

    private static void emitProgress(Flow.Subscriber<String> subscriber, String message) {
        try {
            subscriber.onNext(message);
        } catch (Exception exception) {
            log.debug("[DashboardRegenerate] Progress emit failed: {}", exception.getMessage());
        }
    }

    private void executeToolBatch(
            String userId,
            List<Message> history,
            LlmResponse llmResponse,
            AtomicReference<String> lastToolSnapshot,
            String userMessageId,
            Flow.Subscriber<String> responseSubscriber
    ) {
        Message assistantToolCallMessage = new Message(
                newId(),
                "dashboard-regen",
                MessageRole.ASSISTANT,
                null,
                llmResponse.toolCalls(),
                List.of(),
                Instant.now()
        );
        history.add(assistantToolCallMessage);

        List<ToolResult> toolResults = new ArrayList<>();
        StringBuilder inBatchToolResults = new StringBuilder();
        String basePreflightContext = buildConversationContextForPreflight(history, userMessageId);
        for (ToolCall toolCall : llmResponse.toolCalls()) {
            emitProgress(responseSubscriber, "Running " + toolCall.name() + "…\n");
            McpInvocation invocation = toInvocation(userId, toolCall);
            McpInvocationResult result = invokeWithPreflight(invocation, basePreflightContext, inBatchToolResults.toString());
            toolResults.add(toToolResult(result));
            if (result.success() && result.content() != null && !result.content().isBlank()) {
                appendPreflightToolResult(inBatchToolResults, invocation.toolName(), result.content());
            }
            if (result.success() && result.content() != null && !result.content().isBlank()) {
                lastToolSnapshot.set(result.content());
            }
        }

        Message toolMessage = new Message(
                newId(),
                "dashboard-regen",
                MessageRole.TOOL,
                null,
                List.of(),
                toolResults,
                Instant.now()
        );
        history.add(toolMessage);
    }

    private void persistRegeneratedTurn(
            String userId,
            DashboardTurn existingTurn,
            String prompt,
            String assistantContent,
            String toolResultSnapshot
    ) {
        List<WidgetSpec> widgets = WidgetBlockParser.parseFromContent(assistantContent)
                .map(block -> block.widgets())
                .orElse(List.of());

        insightsDashboardService.updateTurnAfterRegenerate(
                userId,
                existingTurn,
                prompt,
                assistantContent,
                widgets,
                toolResultSnapshot
        );
    }

    private McpInvocationResult invokeWithPreflight(
            McpInvocation invocation,
            String conversationContext,
            String inBatchToolResults
    ) {
        String fullContext = conversationContext;
        if (inBatchToolResults != null && !inBatchToolResults.isBlank()) {
            fullContext = conversationContext
                    + "\n"
                    + UserPromptValueMatcher.TOOL_RESULTS_THIS_BATCH_MARKER
                    + inBatchToolResults
                    + "]";
        }
        var validation = mcpInvocationPreflightPort.validate(invocation, fullContext);
        if (!validation.allowed()) {
            String message = "Tool '" + invocation.toolName() + "' blocked. " + validation.blockMessage();
            return new McpInvocationResult(invocation.toolCallId(), false, null, message);
        }
        return mcpServerPort.invoke(invocation);
    }

    private McpInvocation toInvocation(String userId, ToolCall toolCall) {
        int separatorIndex = toolCall.name().indexOf('.');
        if (separatorIndex <= 0) {
            log.warn("[DashboardRegenerate] Tool name missing prefix: {}", toolCall.name());
            return new McpInvocation("unknown", toolCall.name(), toolCall.argumentsJson(), toolCall.id());
        }
        String prefix = toolCall.name().substring(0, separatorIndex);
        String toolName = toolCall.name().substring(separatorIndex + 1);
        Optional<McpUserConnection> connection = mcpRegistryPort.findByUserIdAndServerIdPrefix(userId, prefix);
        String connectionId = connection.map(McpUserConnection::id).orElse(prefix);
        return new McpInvocation(connectionId, toolName, toolCall.argumentsJson(), toolCall.id());
    }

    private ToolResult toToolResult(McpInvocationResult result) {
        String content = result.success() ? result.content() : result.errorMessage();
        return new ToolResult(result.toolCallId(), content);
    }

    private void signalError(Flow.Subscriber<String> subscriber, Throwable error) {
        try {
            subscriber.onNext(LlmErrorFormatter.toUserMessage(error));
            subscriber.onComplete();
        } catch (Exception exception) {
            subscriber.onError(exception);
        }
    }

    private static String newId() {
        return "msg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildConversationContextForPreflight(List<Message> history, String currentTurnUserMessageId) {
        StringBuilder context = new StringBuilder();
        StringBuilder toolResultsThisTurn = new StringBuilder();
        Set<String> calledToolNames = new LinkedHashSet<>();
        boolean inCurrentTurn = currentTurnUserMessageId == null || currentTurnUserMessageId.isBlank();
        for (Message message : history) {
            if (!inCurrentTurn && currentTurnUserMessageId.equals(message.id())) {
                inCurrentTurn = true;
            }
            if (inCurrentTurn) {
                for (ToolCall toolCall : message.toolCalls()) {
                    calledToolNames.add(toolCall.name());
                }
                if (message.role() == MessageRole.TOOL) {
                    for (ToolResult toolResult : message.toolResults()) {
                        appendPreflightToolResult(toolResultsThisTurn, null, toolResult.content());
                    }
                }
            }
            if (message.role() != MessageRole.USER && message.role() != MessageRole.ASSISTANT) {
                continue;
            }
            String content = message.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (!context.isEmpty()) {
                context.append('\n');
            }
            context.append(content);
        }
        if (!calledToolNames.isEmpty()) {
            context.append("\n[tools-called-this-turn: ")
                    .append(String.join(", ", calledToolNames))
                    .append(']');
        }
        if (!toolResultsThisTurn.isEmpty()) {
            context.append('\n')
                    .append(UserPromptValueMatcher.TOOL_RESULTS_THIS_TURN_MARKER)
                    .append(toolResultsThisTurn)
                    .append(']');
        }
        return context.toString();
    }

    private static final int PREFLIGHT_TOOL_RESULT_MAX_CHARS = 4_000;

    private static void appendPreflightToolResult(StringBuilder buffer, String toolName, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!buffer.isEmpty()) {
            buffer.append('\n');
        }
        if (toolName != null && !toolName.isBlank()) {
            buffer.append(toolName).append(" -> ");
        }
        String collapsed = content.strip();
        if (collapsed.length() <= PREFLIGHT_TOOL_RESULT_MAX_CHARS) {
            buffer.append(collapsed);
            return;
        }
        buffer.append(collapsed, 0, PREFLIGHT_TOOL_RESULT_MAX_CHARS).append("…(truncated)");
    }
}
