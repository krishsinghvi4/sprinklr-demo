package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.application.service.tool.ToolSelectionService;
import com.example.sprinklr.marketplace.domain.model.ChatRequest;
import com.example.sprinklr.marketplace.domain.model.Conversation;
import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.PendingWorkflowState;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.model.ToolResult;
import com.example.sprinklr.marketplace.domain.model.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.port.inbound.ChatUseCase;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.domain.port.outbound.PendingWorkflowPort;
import com.example.sprinklr.marketplace.infrastructure.config.ChatProperties;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates chat turns, deciding when to call MCP tools and when to answer directly.
 * Runs the agentic loop and persists all messages to the conversation store.
 */
@Service
public class ChatOrchestrator implements ChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);
    private static final String TOOL_LIMIT_MESSAGE =
            "Tool call limit reached for this request. Please try a simpler question.";

    private final ChatHistoryPort chatHistoryPort;
    private final LlmPort llmPort;
    private final McpServerPort mcpServerPort;
    private final McpRegistryPort mcpRegistryPort;
    private final McpProperties mcpProperties;
    private final ChatProperties chatProperties;
    private final McpInvocationPreflightPort mcpInvocationPreflightPort;
    private final ToolSelectionService toolSelectionService;
    private final PendingWorkflowPort pendingWorkflowPort;

    public ChatOrchestrator(
            ChatHistoryPort chatHistoryPort,
            LlmPort llmPort,
            McpServerPort mcpServerPort,
            McpRegistryPort mcpRegistryPort,
            McpProperties mcpProperties,
            ChatProperties chatProperties,
            McpInvocationPreflightPort mcpInvocationPreflightPort,
            ToolSelectionService toolSelectionService,
            PendingWorkflowPort pendingWorkflowPort
    ) {
        this.chatHistoryPort = chatHistoryPort;
        this.llmPort = llmPort;
        this.mcpServerPort = mcpServerPort;
        this.mcpRegistryPort = mcpRegistryPort;
        this.mcpProperties = mcpProperties;
        this.chatProperties = chatProperties;
        this.mcpInvocationPreflightPort = mcpInvocationPreflightPort;
        this.toolSelectionService = toolSelectionService;
        this.pendingWorkflowPort = pendingWorkflowPort;
    }

    /**
     * Entry point for streaming chat; runs the agentic loop on a worker thread.
     */
    @Override
    public void streamChat(ChatRequest request, Flow.Subscriber<String> responseSubscriber) {
        Mono.fromRunnable(() -> runAgenticLoop(request, responseSubscriber))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {},
                        error -> signalError(responseSubscriber, error)
                );
    }

    /**
     * Drives the turn: call LLM, invoke tools if requested, then respond with the final text.
     */
    private void runAgenticLoop(ChatRequest request, Flow.Subscriber<String> responseSubscriber) {
        long turnStartMs = System.currentTimeMillis();
        Set<String> usedConnections = new HashSet<>();
        StreamingSession streamingSession = new StreamingSession(responseSubscriber);
        try {
            long setupStartMs = System.currentTimeMillis();
            String conversationId = resolveConversationId(request);
            int priorTurnLimit = Math.max(0, chatProperties.getHistoryTurnLimit() - 1);
            List<Message> history = new ArrayList<>();
            if (priorTurnLimit > 0) {
                history.addAll(chatHistoryPort.findRecentTurns(conversationId, priorTurnLimit));
            }

            Message userMessage = new Message(
                    newId(),
                    conversationId,
                    MessageRole.USER,
                    request.prompt(),
                    List.of(),
                    List.of(),
                    Instant.now()
            );
            chatHistoryPort.saveMessage(userMessage);
            chatHistoryPort.touchConversation(conversationId, request.prompt());
            history.add(userMessage);
            String currentTurnUserMessageId = userMessage.id();
            List<String> currentTurnToolMessageIds = new ArrayList<>();

            List<McpTool> userTools = mcpRegistryPort.findActiveToolsForUser(request.userId());

            // Stage 1+2: scope the tool set for this turn (router pick + deterministic dependency expansion).
            // When the feature is off, fall back to sending every active tool (legacy behavior).
            TurnToolScope scope = resolveTurnToolScope(request, conversationId, history, userTools);
            List<McpTool> toolsForTurn = scope.tools();
            String continuationContext = scope.continuationContext();

            long setupMs = System.currentTimeMillis() - setupStartMs;
            log.info("[Orchestrator] Setup complete in {}ms conversationId={} userTools={} scopedTools={} continuation={} historyTurnLimit={} priorTurnsLoaded={}",
                    setupMs, conversationId, userTools.size(), toolsForTurn.size(),
                    continuationContext != null, chatProperties.getHistoryTurnLimit(), priorTurnLimit);

            // Accumulate tools executed this turn so continuation state can be persisted for the next turn.
            List<String> executedToolNames = new ArrayList<>();
            List<String> executedToolSummaries = new ArrayList<>();

            int iteration = 0;
            int totalToolCalls = 0;

            while (iteration < mcpProperties.getMaxAgenticIterations()) {
                long llmStartMs = System.currentTimeMillis();
                LlmResponse llmResponse = llmPort.complete(
                        new LlmRequest(
                                request.prompt(),
                                history,
                                toolsForTurn,
                                currentTurnUserMessageId,
                                request.userId(),
                                conversationId,
                                continuationContext
                        )
                );
                long llmMs = System.currentTimeMillis() - llmStartMs;
                String responseType = llmResponse.toolCalls().isEmpty() ? "text" : "tool_calls";
                log.info("[Orchestrator] LLM iteration {} completed in {}ms conversationId={} responseType={} toolCallCount={}",
                        iteration, llmMs, conversationId, responseType, llmResponse.toolCalls().size());

                if (llmResponse.toolCalls().isEmpty()) {
                    if (iteration == 0) {
                        handleTextOnlyResponse(conversationId, llmResponse, responseSubscriber);
                    } else {
                        handlePostToolResponse(
                                conversationId,
                                llmResponse,
                                currentTurnToolMessageIds,
                                streamingSession
                        );
                    }
                    persistContinuationIfNeeded(request, conversationId, executedToolNames, executedToolSummaries);
                    logTurnComplete(conversationId, turnStartMs, iteration, totalToolCalls);
                    return;
                }

                if (totalToolCalls + llmResponse.toolCalls().size() > mcpProperties.getMaxToolCallsPerTurn()) {
                    log.info("[Orchestrator] Tool call limit reached conversationId={} total={}",
                            conversationId, totalToolCalls);
                    deliverLimitMessage(conversationId, responseSubscriber);
                    persistContinuationIfNeeded(request, conversationId, executedToolNames, executedToolSummaries);
                    logTurnComplete(conversationId, turnStartMs, iteration, totalToolCalls);
                    return;
                }

                String toolMessageId = executeToolBatch(
                        request,
                        conversationId,
                        history,
                        llmResponse,
                        usedConnections,
                        streamingSession,
                        executedToolNames,
                        executedToolSummaries
                );
                currentTurnToolMessageIds.add(toolMessageId);
                totalToolCalls += llmResponse.toolCalls().size();
                iteration++;
            }

            log.info("[Orchestrator] Agentic iteration limit reached conversationId={}", conversationId);
            deliverLimitMessage(conversationId, responseSubscriber);
            persistContinuationIfNeeded(request, conversationId, executedToolNames, executedToolSummaries);
            logTurnComplete(conversationId, turnStartMs, iteration, totalToolCalls);
        } catch (Exception exception) {
            signalError(responseSubscriber, exception);
            log.warn("[Orchestrator] Turn failed after {}ms: {}",
                    System.currentTimeMillis() - turnStartMs, exception.getMessage());
        }
    }

    private void logTurnComplete(String conversationId, long turnStartMs, int iterations, int totalToolCalls) {
        log.info("[Orchestrator] Turn complete conversationId={} totalMs={} iterations={} toolCalls={}",
                conversationId, System.currentTimeMillis() - turnStartMs, iterations, totalToolCalls);
    }

    /**
     * Resolves which tools to expose to the agent LLM this turn. With the feature enabled, this is the
     * router-selected + dependency-expanded scoped set; otherwise it is every active tool (legacy).
     * Any failure falls back to all tools so chat is never broken.
     */
    private TurnToolScope resolveTurnToolScope(
            ChatRequest request,
            String conversationId,
            List<Message> history,
            List<McpTool> userTools
    ) {
        if (!mcpProperties.getToolSelection().isEnabled() || userTools.isEmpty()) {
            return new TurnToolScope(userTools, null);
        }
        try {
            List<ToolDependencyGraph> graphs =
                    mcpRegistryPort.findActiveDependencyGraphsForUser(request.userId());
            Optional<PendingWorkflowState> continuation =
                    pendingWorkflowPort.find(conversationId, request.userId());
            ToolSelectionResult selection = toolSelectionService.selectTools(
                    request.prompt(), history, userTools, graphs, continuation);
            List<McpTool> tools = selection.scopedTools().isEmpty() ? userTools : selection.scopedTools();
            return new TurnToolScope(tools, selection.continuationContext());
        } catch (Exception exception) {
            log.warn("[Orchestrator] Tool selection failed — using all {} tools: {}",
                    userTools.size(), exception.getMessage());
            return new TurnToolScope(userTools, null);
        }
    }

    /**
     * Persists continuation state at the end of a turn that ran tools, so a follow-up turn can reuse
     * the gathered results instead of re-running prerequisite/metadata tools. No-op when nothing ran.
     */
    private void persistContinuationIfNeeded(
            ChatRequest request,
            String conversationId,
            List<String> executedToolNames,
            List<String> executedToolSummaries
    ) {
        if (!mcpProperties.getToolSelection().isEnabled() || executedToolNames.isEmpty()) {
            return;
        }
        Set<String> prefixes = new LinkedHashSet<>();
        for (String name : executedToolNames) {
            int dot = name.indexOf('.');
            prefixes.add(dot > 0 ? name.substring(0, dot) : name);
        }
        Instant expiresAt = Instant.now()
                .plus(mcpProperties.getToolSelection().getContinuationTtlHours(), ChronoUnit.HOURS);
        try {
            pendingWorkflowPort.save(new PendingWorkflowState(
                    conversationId,
                    request.userId(),
                    new ArrayList<>(prefixes),
                    executedToolNames,
                    executedToolSummaries,
                    expiresAt
            ));
        } catch (Exception exception) {
            log.warn("[Orchestrator] Failed to persist continuation conversationId={}: {}",
                    conversationId, exception.getMessage());
        }
    }

    /** Captures a successful tool result as compact continuation context for the next turn. */
    private void recordExecutedTool(
            String toolName,
            McpInvocationResult result,
            List<String> executedToolNames,
            List<String> executedToolSummaries
    ) {
        if (!result.success()) {
            return;
        }
        if (!executedToolNames.contains(toolName)) {
            executedToolNames.add(toolName);
        }
        String content = result.content() != null ? result.content() : "";
        executedToolSummaries.add(toolName + " -> " + truncateForSummary(content));
    }

    private static String truncateForSummary(String content) {
        String collapsed = content.strip();
        int max = 800;
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "…(truncated)";
    }

    /** Tools to expose this turn plus any continuation context to inject into the agent system prompt. */
    private record TurnToolScope(List<McpTool> tools, String continuationContext) {
        private TurnToolScope {
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    /**
     * Executes a batch of tool calls and records tool results in history.
     *
     * @return id of the persisted TOOL message row
     */
    private String executeToolBatch(
            ChatRequest request,
            String conversationId,
            List<Message> history,
            LlmResponse llmResponse,
            Set<String> usedConnections,
            StreamingSession streamingSession,
            List<String> executedToolNames,
            List<String> executedToolSummaries
    ) {
        Message assistantToolCallMessage = new Message(
                newId(),
                conversationId,
                MessageRole.ASSISTANT,
                null,
                llmResponse.toolCalls(),
                List.of(),
                Instant.now()
        );
        chatHistoryPort.saveMessage(assistantToolCallMessage);
        history.add(assistantToolCallMessage);

        List<ToolCall> toolCalls = llmResponse.toolCalls();
        List<McpInvocation> invocations = toolCalls.stream()
                .map(toolCall -> toInvocation(request.userId(), toolCall))
                .peek(invocation -> usedConnections.add(invocation.serverId()))
                .toList();

        List<McpInvocationResult> invocationResults = new ArrayList<>();
        for (int i = 0; i < invocations.size(); i++) {
            McpInvocation invocation = invocations.get(i);
            streamingSession.emitProgress("Running " + invocation.toolName() + "…\n");
            long toolStartMs = System.currentTimeMillis();
            McpInvocationResult result = invokeWithPreflight(
                    invocation,
                    buildConversationContextForPreflight(history)
            );
            long toolMs = System.currentTimeMillis() - toolStartMs;
            log.info("[Orchestrator] MCP tool {} completed in {}ms conversationId={} success={}",
                    invocation.toolName(), toolMs, conversationId, result.success());
            invocationResults.add(result);
            // Record the fully-qualified name + a compact summary for cross-turn continuation reuse.
            recordExecutedTool(toolCalls.get(i).name(), result, executedToolNames, executedToolSummaries);
        }

        List<ToolResult> toolResults = invocationResults.stream()
                .map(this::toToolResult)
                .toList();

        Message toolMessage = new Message(
                newId(),
                conversationId,
                MessageRole.TOOL,
                null,
                List.of(),
                toolResults,
                Instant.now()
        );
        chatHistoryPort.saveMessage(toolMessage);
        history.add(toolMessage);
        return toolMessage.id();
    }

    /**
     * Delivers the final agentic LLM text after tool execution (no extra summary LLM call).
     */
    private void handlePostToolResponse(
            String conversationId,
            LlmResponse llmResponse,
            List<String> currentTurnToolMessageIds,
            StreamingSession streamingSession
    ) {
        String content = llmResponse.content() != null ? llmResponse.content() : "";
        log.info("[Orchestrator] Post-tool response conversationId={} chars={}",
                conversationId, content.length());

        streamingSession.emitFinal(content);
        persistPostToolAssistantMessage(conversationId, content, currentTurnToolMessageIds);
    }

    private void persistPostToolAssistantMessage(
            String conversationId,
            String content,
            List<String> currentTurnToolMessageIds
    ) {
        if (!content.isEmpty()) {
            Message assistantMessage = new Message(
                    newId(),
                    conversationId,
                    MessageRole.ASSISTANT,
                    content,
                    List.of(),
                    List.of(),
                    Instant.now()
            );
            chatHistoryPort.saveMessage(assistantMessage);
        }

        for (String toolMessageId : currentTurnToolMessageIds) {
            chatHistoryPort.truncateToolResults(toolMessageId);
        }
    }

    private void deliverLimitMessage(String conversationId, Flow.Subscriber<String> responseSubscriber) {
        deliverSingleChunk(responseSubscriber, TOOL_LIMIT_MESSAGE);
        Message assistantMessage = new Message(
                newId(),
                conversationId,
                MessageRole.ASSISTANT,
                TOOL_LIMIT_MESSAGE,
                List.of(),
                List.of(),
                Instant.now()
        );
        chatHistoryPort.saveMessage(assistantMessage);
    }

    private void handleTextOnlyResponse(
            String conversationId,
            LlmResponse llmResponse,
            Flow.Subscriber<String> responseSubscriber
    ) {
        String content = llmResponse.content() != null ? llmResponse.content() : "";
        log.info("[Orchestrator] Text-only response conversationId={} chars={}",
                conversationId, content.length());

        deliverSingleChunk(responseSubscriber, content);

        Message assistantMessage = new Message(
                newId(),
                conversationId,
                MessageRole.ASSISTANT,
                content,
                List.of(),
                List.of(),
                Instant.now()
        );
        chatHistoryPort.saveMessage(assistantMessage);
    }

    private String resolveConversationId(ChatRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            Optional<Conversation> existing = chatHistoryPort.findConversationByIdAndUserId(
                    request.conversationId(),
                    request.userId()
            );
            if (existing.isPresent()) {
                return existing.get().id();
            }

            if (chatHistoryPort.findConversationById(request.conversationId()).isPresent()) {
                throw new IllegalStateException("Conversation does not belong to user");
            }

            Instant now = Instant.now();
            Conversation conversation = new Conversation(
                    request.conversationId(),
                    request.userId(),
                    null,
                    now,
                    now
            );
            return chatHistoryPort.saveConversation(conversation).id();
        }

        Instant now = Instant.now();
        Conversation conversation = new Conversation(
                newId(),
                request.userId(),
                null,
                now,
                now
        );
        return chatHistoryPort.saveConversation(conversation).id();
    }

    private McpInvocationResult invokeWithPreflight(McpInvocation invocation, String conversationContext) {
        log.info("[Orchestrator] Preflight check starting tool={} connectionId={} (runs after LLM tool_calls, before MCP invoke)",
                invocation.toolName(), invocation.serverId());
        var validation = mcpInvocationPreflightPort.validate(invocation, conversationContext);
        if (!validation.allowed()) {
            log.info("[Orchestrator] Blocked tool={} via preflight guard: {}",
                    invocation.toolName(), validation.blockMessage());
            String message = "Tool '" + invocation.toolName() + "' blocked. " + validation.blockMessage();
            return new McpInvocationResult(invocation.toolCallId(), false, null, message);
        }
        log.info("[Orchestrator] Preflight passed tool={} — invoking MCP", invocation.toolName());
        return mcpServerPort.invoke(invocation);
    }

    /**
     * Builds USER + ASSISTANT text from the loaded history window for preflight guards.
     * Guards use this (not just the latest user message) so values from earlier turns or
     * assistant confirmations (e.g. user replying "yes") still validate.
     */
    private String buildConversationContextForPreflight(List<Message> history) {
        StringBuilder context = new StringBuilder();
        Set<String> calledToolNames = new LinkedHashSet<>();
        for (Message message : history) {
            // Collect tool names already requested this turn so the dependency guard can tell which
            // prerequisites have run. Names are appended below as a marker line, not free-form text.
            for (ToolCall toolCall : message.toolCalls()) {
                calledToolNames.add(toolCall.name());
            }
            if (message.role() != MessageRole.USER && message.role() != MessageRole.ASSISTANT) {
                continue;
            }
            String content = message.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (context.length() > 0) {
                context.append('\n');
            }
            context.append(content);
        }
        if (!calledToolNames.isEmpty()) {
            context.append("\n[tools-called-this-turn: ")
                    .append(String.join(", ", calledToolNames))
                    .append(']');
        }
        return context.toString();
    }

    private McpInvocation toInvocation(String userId, ToolCall toolCall) {
        int separatorIndex = toolCall.name().indexOf('.');
        if (separatorIndex <= 0) {
            log.warn("[Orchestrator] Tool name missing prefix: {}", toolCall.name());
            return new McpInvocation(
                    "unknown",
                    toolCall.name(),
                    toolCall.argumentsJson(),
                    toolCall.id()
            );
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

    private void signalError(Flow.Subscriber<String> responseSubscriber, Throwable error) {
        try {
            if (LlmErrorFormatter.isVpnOrNetworkFailure(error)) {
                log.warn("[Orchestrator] LLM router unreachable: {}", error.getMessage());
            } else if (LlmErrorFormatter.isTransientNetworkFailure(error)) {
                log.warn("[Orchestrator] LLM connection interrupted: {}", error.getMessage());
            } else {
                log.error("[Orchestrator] Chat failed: {}", error.getMessage());
            }
            deliverSingleChunk(responseSubscriber, LlmErrorFormatter.toUserMessage(error));
        } catch (Exception ignored) {
            // Subscriber may already be terminated.
        }
    }

    private void deliverSingleChunk(Flow.Subscriber<String> responseSubscriber, String content) {
        responseSubscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (!content.isEmpty()) {
                    responseSubscriber.onNext(content);
                }
                responseSubscriber.onComplete();
            }

            @Override
            public void cancel() {
            }
        });
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Manages multi-chunk SSE delivery for tool-turn progress and final response.
     */
    private static final class StreamingSession {
        private final Flow.Subscriber<String> subscriber;
        private final AtomicBoolean subscribed = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private StreamingSession(Flow.Subscriber<String> subscriber) {
            this.subscriber = subscriber;
        }

        void emitProgress(String chunk) {
            if (chunk == null || chunk.isEmpty() || completed.get()) {
                return;
            }
            ensureSubscribed();
            subscriber.onNext(chunk);
        }

        void emitFinal(String content) {
            if (completed.get()) {
                return;
            }
            ensureSubscribed();
            if (content != null && !content.isEmpty()) {
                subscriber.onNext(content);
            }
            subscriber.onComplete();
            completed.set(true);
        }

        private void ensureSubscribed() {
            if (subscribed.compareAndSet(false, true)) {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        // ChatController requests Long.MAX_VALUE; chunks are pushed as produced.
                    }

                    @Override
                    public void cancel() {
                        completed.set(true);
                    }
                });
            }
        }
    }
}
