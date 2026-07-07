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
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogToolSelectionSupport;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight.UserPromptValueMatcher;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedQueryPreferencesToolDescriptionAugmenter;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCachePreflightSupport;
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
    private final McpCatalogToolSelectionSupport catalogToolSelectionSupport;
    private final RedSampleQueryCachePreflightSupport redSampleQueryCachePreflightSupport;
    private final RedQueryPreferencesToolDescriptionAugmenter redQueryPreferencesToolDescriptionAugmenter;

    public ChatOrchestrator(
            ChatHistoryPort chatHistoryPort,
            LlmPort llmPort,
            McpServerPort mcpServerPort,
            McpRegistryPort mcpRegistryPort,
            McpProperties mcpProperties,
            ChatProperties chatProperties,
            McpInvocationPreflightPort mcpInvocationPreflightPort,
            ToolSelectionService toolSelectionService,
            PendingWorkflowPort pendingWorkflowPort,
            McpCatalogToolSelectionSupport catalogToolSelectionSupport,
            RedSampleQueryCachePreflightSupport redSampleQueryCachePreflightSupport,
            RedQueryPreferencesToolDescriptionAugmenter redQueryPreferencesToolDescriptionAugmenter
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
        this.catalogToolSelectionSupport = catalogToolSelectionSupport;
        this.redSampleQueryCachePreflightSupport = redSampleQueryCachePreflightSupport;
        this.redQueryPreferencesToolDescriptionAugmenter = redQueryPreferencesToolDescriptionAugmenter;
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

            List<McpTool> userTools = redQueryPreferencesToolDescriptionAugmenter.augment(
                    request.userId(),
                    mcpRegistryPort.findActiveToolsForUser(request.userId()));

            // Stage 1+2: scope the tool set for this turn (router pick + deterministic dependency expansion).
            // When the feature is off, fall back to sending every active tool (legacy behavior).
            TurnToolScope scope = resolveTurnToolScope(request, conversationId, history, userTools);
            List<McpTool> toolsForTurn = new ArrayList<>(scope.tools());
            String continuationContext = scope.continuationContext();
            List<String> primaryToolNames = scope.primaryToolNames();

            long setupMs = System.currentTimeMillis() - setupStartMs;
            log.info("[Orchestrator] Setup complete in {}ms conversationId={} userTools={} scopedTools={} continuation={} historyTurnLimit={} priorTurnsLoaded={}",
                    setupMs, conversationId, userTools.size(), toolsForTurn.size(),
                    continuationContext != null, chatProperties.getHistoryTurnLimit(), priorTurnLimit);

            // Accumulate tools executed this turn so continuation state can be persisted for the next turn.
            List<String> executedToolNames = new ArrayList<>();
            List<String> executedToolSummaries = new ArrayList<>();
            String agentAdditionalContext = continuationContext;

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
                                agentAdditionalContext
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
                    persistContinuationIfNeeded(
                            request, conversationId, iteration, primaryToolNames,
                            executedToolNames, executedToolSummaries);
                    logTurnComplete(conversationId, turnStartMs, iteration, totalToolCalls);
                    return;
                }

                if (totalToolCalls + llmResponse.toolCalls().size() > mcpProperties.getMaxToolCallsPerTurn()) {
                    log.info("[Orchestrator] Tool call limit reached conversationId={} total={}",
                            conversationId, totalToolCalls);
                    deliverLimitMessage(conversationId, responseSubscriber);
                    persistContinuationIfNeeded(
                            request, conversationId, iteration, primaryToolNames,
                            executedToolNames, executedToolSummaries);
                    logTurnComplete(conversationId, turnStartMs, iteration, totalToolCalls);
                    return;
                }

                ToolBatchResult batchResult = executeToolBatch(
                        request,
                        conversationId,
                        history,
                        currentTurnUserMessageId,
                        llmResponse,
                        usedConnections,
                        streamingSession,
                        executedToolNames,
                        executedToolSummaries
                );
                currentTurnToolMessageIds.add(batchResult.toolMessageId());
                totalToolCalls += llmResponse.toolCalls().size();
                iteration++;
            }

            log.info("[Orchestrator] Agentic iteration limit reached conversationId={}", conversationId);
            deliverLimitMessage(conversationId, responseSubscriber);
            persistContinuationIfNeeded(
                    request, conversationId, iteration, primaryToolNames,
                    executedToolNames, executedToolSummaries);
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
            return new TurnToolScope(userTools, null, List.of());
        }
        try {
            List<ToolDependencyGraph> graphs =
                    mcpRegistryPort.findActiveDependencyGraphsForUser(request.userId());
            Optional<PendingWorkflowState> storedContinuation =
                    pendingWorkflowPort.find(conversationId, request.userId());
            ToolSelectionResult selection = toolSelectionService.selectTools(
                    request.prompt(), history, userTools, graphs, storedContinuation);
            if (selection.continuationDiscarded()) {
                pendingWorkflowPort.delete(conversationId);
            }
            return new TurnToolScope(
                    selection.scopedTools(), selection.continuationContext(), selection.primaryToolNames());
        } catch (Exception exception) {
            log.warn("[Orchestrator] Tool selection failed — using all {} tools: {}",
                    userTools.size(), exception.getMessage());
            return new TurnToolScope(userTools, null, List.of());
        }
    }

    /**
     * Persists continuation only for incomplete multi-step workflows (goal tools scoped but not executed).
     * Deletes continuation when the turn delivered a final answer or no tools ran.
     */
    private void persistContinuationIfNeeded(
            ChatRequest request,
            String conversationId,
            int iteration,
            List<String> primaryToolNames,
            List<String> executedToolNames,
            List<String> executedToolSummaries
    ) {
        if (!mcpProperties.getToolSelection().isEnabled()) {
            return;
        }
        if (primaryToolNames.isEmpty()) {
            return;
        }

        Set<String> executed = new HashSet<>(executedToolNames);
        List<String> awaitingGoalTools = primaryToolNames.stream()
                .filter(name -> !executed.contains(name))
                .toList();

        if (awaitingGoalTools.isEmpty()) {
            pendingWorkflowPort.delete(conversationId);
            return;
        }

        Set<String> prefixes = new LinkedHashSet<>();
        for (String name : executedToolNames) {
            int dot = name.indexOf('.');
            prefixes.add(dot > 0 ? name.substring(0, dot) : name);
        }
        for (String name : primaryToolNames) {
            int dot = name.indexOf('.');
            prefixes.add(dot > 0 ? name.substring(0, dot) : name);
        }
        Set<String> neverSatisfy = catalogToolSelectionSupport.continuationNeverSatisfyToolsForToolNames(
                executedToolNames);
        List<String> satisfiedForContinuation = executedToolNames.stream()
                .filter(name -> !neverSatisfy.contains(name))
                .toList();
        List<String> summariesForContinuation = filterSummariesForContinuation(
                executedToolSummaries, neverSatisfy);
        if (summariesForContinuation.isEmpty() && executedToolNames.isEmpty()) {
            summariesForContinuation = List.of("Awaiting user input to continue workflow");
        }

        Instant expiresAt = Instant.now()
                .plus(mcpProperties.getToolSelection().getContinuationTtlHours(), ChronoUnit.HOURS);
        try {
            pendingWorkflowPort.save(new PendingWorkflowState(
                    conversationId,
                    request.userId(),
                    new ArrayList<>(prefixes),
                    awaitingGoalTools,
                    satisfiedForContinuation,
                    summariesForContinuation,
                    expiresAt
            ));
        } catch (Exception exception) {
            log.warn("[Orchestrator] Failed to persist continuation conversationId={}: {}",
                    conversationId, exception.getMessage());
        }
    }

    private List<String> filterSummariesForContinuation(
            List<String> summaries, Set<String> neverSatisfy) {
        List<String> filtered = new ArrayList<>();
        for (String summary : summaries) {
            int arrow = summary.indexOf(" -> ");
            if (arrow <= 0) {
                continue;
            }
            String toolName = summary.substring(0, arrow);
            if (!neverSatisfy.contains(toolName)) {
                filtered.add(summary);
            }
        }
        return filtered;
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

    private String truncateForSummary(String content) {
        String collapsed = content.strip();
        int max = Math.max(1, mcpProperties.getToolSelection().getContinuationSummaryMaxChars());
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "…(truncated)";
    }

    private static Set<String> toolNamesCalledThisTurn(List<Message> history, String currentTurnUserMessageId) {
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
            }
        }
        return calledToolNames;
    }

    /** Tools to expose this turn plus continuation context and router primary picks for this turn. */
    private record TurnToolScope(
            List<McpTool> tools,
            String continuationContext,
            List<String> primaryToolNames
    ) {
        private TurnToolScope {
            tools = tools == null ? List.of() : List.copyOf(tools);
            primaryToolNames = primaryToolNames == null ? List.of() : List.copyOf(primaryToolNames);
        }
    }

    /**
     * Executes a batch of tool calls and records tool results in history.
     */
    private ToolBatchResult executeToolBatch(
            ChatRequest request,
            String conversationId,
            List<Message> history,
            String currentTurnUserMessageId,
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
        // chatHistoryPort.saveMessage(assistantToolCallMessage); // debug: skip DB persist for tool calls
        history.add(assistantToolCallMessage);

        List<ToolCall> toolCalls = llmResponse.toolCalls();
        List<McpInvocation> invocations = toolCalls.stream()
                .map(toolCall -> toInvocation(request.userId(), toolCall))
                .peek(invocation -> usedConnections.add(invocation.serverId()))
                .toList();

        List<McpInvocationResult> invocationResults = new ArrayList<>();
        StringBuilder inBatchToolResults = new StringBuilder();
        String basePreflightContext = buildConversationContextForPreflight(history, currentTurnUserMessageId);
        Set<String> calledToolNamesThisTurn = toolNamesCalledThisTurn(history, currentTurnUserMessageId);
        for (int i = 0; i < invocations.size(); i++) {
            McpInvocation invocation = invocations.get(i);
            redSampleQueryCachePreflightSupport.findCachedSampleForExecute(invocation, calledToolNamesThisTurn)
                    .ifPresent(hit -> appendPreflightToolResult(
                            inBatchToolResults,
                            hit.sampleToolLabel() + " (cached)",
                            hit.resultContent()
                    ));
            streamingSession.emitProgress("Running " + invocation.toolName() + "…\n");
            long toolStartMs = System.currentTimeMillis();
            McpInvocationResult result = invokeWithPreflight(
                    invocation,
                    basePreflightContext,
                    inBatchToolResults.toString()
            );
            long toolMs = System.currentTimeMillis() - toolStartMs;
            log.info("[Orchestrator] MCP tool {} completed in {}ms conversationId={} success={}",
                    invocation.toolName(), toolMs, conversationId, result.success());
            invocationResults.add(result);
            if (result.success() && result.content() != null && !result.content().isBlank()) {
                appendPreflightToolResult(inBatchToolResults, invocation.toolName(), result.content());
            }
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
        // chatHistoryPort.saveMessage(toolMessage); // debug: skip DB persist for tool results
        history.add(toolMessage);
        return new ToolBatchResult(toolMessage.id());
    }

    private record ToolBatchResult(String toolMessageId) {
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

        // for (String toolMessageId : currentTurnToolMessageIds) {
        //     chatHistoryPort.truncateToolResults(toolMessageId);
        // }
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

    private McpInvocationResult invokeWithPreflight(
            McpInvocation invocation,
            String conversationContext,
            String inBatchToolResults
    ) {
        log.info("[Orchestrator] Preflight check starting tool={} connectionId={} (runs after LLM tool_calls, before MCP invoke)",
                invocation.toolName(), invocation.serverId());
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
            log.info("[Orchestrator] Blocked tool={} via preflight guard: {}",
                    invocation.toolName(), validation.blockMessage());
            String message = "Tool '" + invocation.toolName() + "' blocked. " + validation.blockMessage();
            return new McpInvocationResult(invocation.toolCallId(), false, null, message);
        }
        log.info("[Orchestrator] Preflight passed tool={} — invoking MCP", invocation.toolName());
        return mcpServerPort.invoke(invocation);
    }

    /**
     * Builds USER + ASSISTANT text and same-turn TOOL results for preflight guards.
     * Guards use this (not just the latest user message) so values from earlier turns,
     * assistant confirmations, or tool discovery in this turn still validate.
     */
    private String buildConversationContextForPreflight(List<Message> history, String currentTurnUserMessageId) {
        StringBuilder context = new StringBuilder();
        StringBuilder toolResultsThisTurn = new StringBuilder();
        Set<String> calledToolNames = new LinkedHashSet<>();
        boolean inCurrentTurn = currentTurnUserMessageId == null || currentTurnUserMessageId.isBlank();
        for (Message message : history) {
            if (!inCurrentTurn && currentTurnUserMessageId.equals(message.id())) {
                inCurrentTurn = true;
            }
            // Collect tool names requested this turn only — prior-turn tool calls must not satisfy guards.
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
        if (toolResultsThisTurn.length() > 0) {
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
        if (buffer.length() > 0) {
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
