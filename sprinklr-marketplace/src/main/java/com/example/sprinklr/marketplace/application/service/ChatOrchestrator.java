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
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.config.ChatProperties;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Flow;

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

    public ChatOrchestrator(
            ChatHistoryPort chatHistoryPort,
            LlmPort llmPort,
            McpServerPort mcpServerPort,
            McpRegistryPort mcpRegistryPort,
            McpProperties mcpProperties,
            ChatProperties chatProperties
    ) {
        this.chatHistoryPort = chatHistoryPort;
        this.llmPort = llmPort;
        this.mcpServerPort = mcpServerPort;
        this.mcpRegistryPort = mcpRegistryPort;
        this.mcpProperties = mcpProperties;
        this.chatProperties = chatProperties;
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
     * Drives the turn: call LLM, invoke tools if requested, then summarize.
     */
    private void runAgenticLoop(ChatRequest request, Flow.Subscriber<String> responseSubscriber) {
        Set<String> usedConnections = new HashSet<>();
        try {
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
            log.info("[Orchestrator] Starting agentic loop conversationId={} userTools={} historyTurnLimit={} priorTurnsLoaded={}",
                    conversationId, userTools.size(), chatProperties.getHistoryTurnLimit(), priorTurnLimit);

            int iteration = 0;
            int totalToolCalls = 0;

            while (iteration < mcpProperties.getMaxAgenticIterations()) {
                LlmResponse llmResponse = llmPort.complete(
                        new LlmRequest(request.prompt(), history, userTools, currentTurnUserMessageId)
                );

                if (llmResponse.toolCalls().isEmpty()) {
                    if (iteration == 0) {
                        handleTextOnlyResponse(conversationId, llmResponse, responseSubscriber);
                    } else {
                        streamSummaryResponse(
                                request,
                                conversationId,
                                history,
                                currentTurnUserMessageId,
                                currentTurnToolMessageIds,
                                responseSubscriber
                        );
                    }
                    return;
                }

                if (totalToolCalls + llmResponse.toolCalls().size() > mcpProperties.getMaxToolCallsPerTurn()) {
                    log.info("[Orchestrator] Tool call limit reached conversationId={} total={}",
                            conversationId, totalToolCalls);
                    deliverLimitMessage(conversationId, responseSubscriber);
                    return;
                }

                String toolMessageId = executeToolBatch(
                        request, conversationId, history, llmResponse, usedConnections
                );
                currentTurnToolMessageIds.add(toolMessageId);
                totalToolCalls += llmResponse.toolCalls().size();
                iteration++;
            }

            log.info("[Orchestrator] Agentic iteration limit reached conversationId={}", conversationId);
            deliverLimitMessage(conversationId, responseSubscriber);
        } catch (Exception exception) {
            signalError(responseSubscriber, exception);
        } finally {
            clearMcpSessions(usedConnections);
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
            Set<String> usedConnections
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

        List<McpInvocationResult> invocationResults = Flux.fromIterable(invocations)
                .concatMap(invocation -> Mono.fromCallable(() -> mcpServerPort.invoke(invocation))
                        .subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .block();

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
     * After tool execution, runs a final LLM pass to summarize and respond.
     */
    private void streamSummaryResponse(
            ChatRequest request,
            String conversationId,
            List<Message> history,
            String currentTurnUserMessageId,
            List<String> currentTurnToolMessageIds,
            Flow.Subscriber<String> responseSubscriber
    ) {
        Flow.Subscriber<String> capturingSummarySubscriber = createCapturingSummarySubscriber(
                conversationId,
                currentTurnToolMessageIds,
                responseSubscriber
        );
        llmPort.streamSummary(
                new LlmRequest(request.prompt(), history, List.of(), currentTurnUserMessageId),
                capturingSummarySubscriber
        );
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

    /**
     * Maps an LLM tool call to an MCP invocation using the tool name prefix.
     */
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

    private Flow.Subscriber<String> createCapturingSummarySubscriber(
            String conversationId,
            List<String> currentTurnToolMessageIds,
            Flow.Subscriber<String> originalSubscriber
    ) {
        return new Flow.Subscriber<String>() {
            private final StringBuilder summaryBuffer = new StringBuilder();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                originalSubscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(String chunk) {
                summaryBuffer.append(chunk);
                originalSubscriber.onNext(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("[Orchestrator] Summary stream error: {}", throwable.getMessage());
                originalSubscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                String completeSummary = summaryBuffer.toString();
                log.info("[Orchestrator] Summary complete conversationId={} chars={}",
                        conversationId, completeSummary.length());

                if (!completeSummary.isEmpty()) {
                    Message assistantSummaryMessage = new Message(
                            newId(),
                            conversationId,
                            MessageRole.ASSISTANT,
                            completeSummary,
                            List.of(),
                            List.of(),
                            Instant.now()
                    );
                    chatHistoryPort.saveMessage(assistantSummaryMessage);
                }

                for (String toolMessageId : currentTurnToolMessageIds) {
                    chatHistoryPort.truncateToolResults(toolMessageId);
                }

                originalSubscriber.onComplete();
            }
        };
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
                responseSubscriber.onNext(content);
                responseSubscriber.onComplete();
            }

            @Override
            public void cancel() {
            }
        });
    }

    private void clearMcpSessions(Set<String> connectionIds) {
        if (connectionIds.isEmpty()) {
            return;
        }
        connectionIds.forEach(mcpRegistryPort::clearSession);
        log.info("[Orchestrator] Cleared MCP sessions count={}", connectionIds.size());
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
