package com.example.sprinklr.marketplace.application.service;

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
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.model.ToolResult;
import com.example.sprinklr.marketplace.domain.port.inbound.ChatUseCase;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.config.ChatProperties;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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

    public ChatOrchestrator(
            ChatHistoryPort chatHistoryPort,
            LlmPort llmPort,
            McpServerPort mcpServerPort,
            McpRegistryPort mcpRegistryPort,
            McpProperties mcpProperties,
            ChatProperties chatProperties,
            McpInvocationPreflightPort mcpInvocationPreflightPort
    ) {
        this.chatHistoryPort = chatHistoryPort;
        this.llmPort = llmPort;
        this.mcpServerPort = mcpServerPort;
        this.mcpRegistryPort = mcpRegistryPort;
        this.mcpProperties = mcpProperties;
        this.chatProperties = chatProperties;
        this.mcpInvocationPreflightPort = mcpInvocationPreflightPort;
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
            long setupMs = System.currentTimeMillis() - setupStartMs;
            log.info("[Orchestrator] Setup complete in {}ms conversationId={} userTools={} historyTurnLimit={} priorTurnsLoaded={}",
                    setupMs, conversationId, userTools.size(), chatProperties.getHistoryTurnLimit(), priorTurnLimit);

            int iteration = 0;
            int totalToolCalls = 0;

            while (iteration < mcpProperties.getMaxAgenticIterations()) {
                long llmStartMs = System.currentTimeMillis();
                LlmResponse llmResponse = llmPort.complete(
                        new LlmRequest(request.prompt(), history, userTools, currentTurnUserMessageId)
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
                    logTurnComplete(conversationId, turnStartMs, iteration, totalToolCalls);
                    return;
                }

                if (totalToolCalls + llmResponse.toolCalls().size() > mcpProperties.getMaxToolCallsPerTurn()) {
                    log.info("[Orchestrator] Tool call limit reached conversationId={} total={}",
                            conversationId, totalToolCalls);
                    deliverLimitMessage(conversationId, responseSubscriber);
                    logTurnComplete(conversationId, turnStartMs, iteration, totalToolCalls);
                    return;
                }

                String toolMessageId = executeToolBatch(
                        request,
                        conversationId,
                        history,
                        llmResponse,
                        usedConnections,
                        streamingSession
                );
                currentTurnToolMessageIds.add(toolMessageId);
                totalToolCalls += llmResponse.toolCalls().size();
                iteration++;
            }

            log.info("[Orchestrator] Agentic iteration limit reached conversationId={}", conversationId);
            deliverLimitMessage(conversationId, responseSubscriber);
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
            StreamingSession streamingSession
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

        List<McpInvocation> invocations = llmResponse.toolCalls().stream()
                .map(toolCall -> toInvocation(request.userId(), toolCall))
                .peek(invocation -> usedConnections.add(invocation.serverId()))
                .toList();

        List<McpInvocationResult> invocationResults = new ArrayList<>();
        for (McpInvocation invocation : invocations) {
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
        for (Message message : history) {
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
