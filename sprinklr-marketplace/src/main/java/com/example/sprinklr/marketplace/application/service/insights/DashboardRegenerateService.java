package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.application.service.tool.ToolSelectionService;
import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.model.ToolResult;
import com.example.sprinklr.marketplace.domain.model.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DashboardRegenerateService {

    private static final Logger log = LoggerFactory.getLogger(DashboardRegenerateService.class);
    private static final String TOOL_LIMIT_MESSAGE =
            "Tool call limit reached for this request. Please try a simpler question.";
    private static final String REGEN_CONTEXT = """
            Dashboard regeneration: call getAccessibleAtlassianResources then getJiraIssueChangelog with fresh data.
            Respond with at most 2 sentences of summary plus exactly one ```widget``` fence containing 4–6 visual widgets.
            Every bar/line/area widget must include xAxisLabel and yAxisLabel in data. Do not use table widgets.""";

    private final LlmPort llmPort;
    private final McpServerPort mcpServerPort;
    private final McpRegistryPort mcpRegistryPort;
    private final McpProperties mcpProperties;
    private final McpInvocationPreflightPort mcpInvocationPreflightPort;
    private final InsightsDashboardService insightsDashboardService;
    private final ToolSelectionService toolSelectionService;

    public DashboardRegenerateService(
            LlmPort llmPort,
            McpServerPort mcpServerPort,
            McpRegistryPort mcpRegistryPort,
            McpProperties mcpProperties,
            McpInvocationPreflightPort mcpInvocationPreflightPort,
            InsightsDashboardService insightsDashboardService,
            ToolSelectionService toolSelectionService
    ) {
        this.llmPort = llmPort;
        this.mcpServerPort = mcpServerPort;
        this.mcpRegistryPort = mcpRegistryPort;
        this.mcpProperties = mcpProperties;
        this.mcpInvocationPreflightPort = mcpInvocationPreflightPort;
        this.insightsDashboardService = insightsDashboardService;
        this.toolSelectionService = toolSelectionService;
    }

    public void streamRegenerate(
            String userId,
            DashboardTurn existingTurn,
            Flow.Subscriber<String> responseSubscriber
    ) {
        Mono.fromRunnable(() -> runRegenerateLoop(userId, existingTurn, responseSubscriber))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {},
                        error -> signalError(responseSubscriber, error)
                );
    }

    private void runRegenerateLoop(
            String userId,
            DashboardTurn existingTurn,
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

            List<Message> history = new ArrayList<>();
            String userMessageId = newId();
            Message userMessage = new Message(
                    userMessageId,
                    "dashboard-regen",
                    MessageRole.USER,
                    existingTurn.prompt(),
                    List.of(),
                    List.of(),
                    Instant.now()
            );
            history.add(userMessage);

            List<McpTool> toolsForTurn = resolveToolsForRegenerate(userId, existingTurn.prompt(), history);
            log.info("[DashboardRegenerate] turnId={} scopedTools={}", existingTurn.id(), toolsForTurn.size());

            int iteration = 0;
            int totalToolCalls = 0;
            AtomicReference<String> lastChangelogSnapshot = new AtomicReference<>(existingTurn.toolResultSnapshot());

            while (iteration < mcpProperties.getMaxAgenticIterations()) {
                LlmResponse llmResponse = llmPort.complete(new LlmRequest(
                        existingTurn.prompt(),
                        history,
                        toolsForTurn,
                        userMessageId,
                        userId,
                        "dashboard-regen-" + existingTurn.id(),
                        REGEN_CONTEXT
                ));

                if (llmResponse.toolCalls().isEmpty()) {
                    String content = llmResponse.content() != null ? llmResponse.content() : "";
                    emitFinal(responseSubscriber, content);
                    persistRegeneratedTurn(userId, existingTurn, content, lastChangelogSnapshot.get());
                    responseSubscriber.onComplete();
                    return;
                }

                if (totalToolCalls + llmResponse.toolCalls().size() > mcpProperties.getMaxToolCallsPerTurn()) {
                    emitFinal(responseSubscriber, TOOL_LIMIT_MESSAGE);
                    responseSubscriber.onComplete();
                    return;
                }

                executeToolBatch(userId, history, llmResponse, responseSubscriber, lastChangelogSnapshot, existingTurn.prompt());
                totalToolCalls += llmResponse.toolCalls().size();
                iteration++;
            }

            emitFinal(responseSubscriber, TOOL_LIMIT_MESSAGE);
            responseSubscriber.onComplete();
        } catch (Exception exception) {
            signalError(responseSubscriber, exception);
        }
    }

    private List<McpTool> resolveToolsForRegenerate(String userId, String prompt, List<Message> history) {
        List<McpTool> userTools = mcpRegistryPort.findActiveToolsForUser(userId);
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
        List<McpTool> jiraTools = userTools.stream()
                .filter(tool -> isJiraRelatedTool(tool))
                .toList();
        if (!jiraTools.isEmpty()) {
            return jiraTools;
        }
        return userTools;
    }

    private static boolean isJiraRelatedTool(McpTool tool) {
        String serverId = tool.serverId() != null ? tool.serverId().toLowerCase(Locale.ROOT) : "";
        String name = tool.name() != null ? tool.name().toLowerCase(Locale.ROOT) : "";
        return serverId.contains("jira") || name.contains("jira");
    }

    private void executeToolBatch(
            String userId,
            List<Message> history,
            LlmResponse llmResponse,
            Flow.Subscriber<String> responseSubscriber,
            AtomicReference<String> lastChangelogSnapshot,
            String promptContext
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
        for (ToolCall toolCall : llmResponse.toolCalls()) {
            emitProgress(responseSubscriber, "Running " + toolCall.name() + "…\n");
            McpInvocation invocation = toInvocation(userId, toolCall);
            McpInvocationResult result = invokeWithPreflight(invocation, promptContext);
            toolResults.add(toToolResult(result));
            if (result.success() && result.content() != null
                    && toolCall.name().toLowerCase().contains("changelog")) {
                lastChangelogSnapshot.set(result.content());
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
            String assistantContent,
            String toolResultSnapshot
    ) {
        List<WidgetSpec> widgets = WidgetBlockParser.parseFromContent(assistantContent)
                .map(block -> block.widgets())
                .orElse(existingTurn.widgets());

        insightsDashboardService.updateTurnAfterRegenerate(
                userId,
                existingTurn,
                assistantContent,
                widgets,
                toolResultSnapshot
        );
    }

    private McpInvocationResult invokeWithPreflight(McpInvocation invocation, String conversationContext) {
        var validation = mcpInvocationPreflightPort.validate(invocation, conversationContext);
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

    private void emitProgress(Flow.Subscriber<String> subscriber, String text) {
        subscriber.onNext(text);
    }

    private void emitFinal(Flow.Subscriber<String> subscriber, String text) {
        subscriber.onNext(text);
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
}
