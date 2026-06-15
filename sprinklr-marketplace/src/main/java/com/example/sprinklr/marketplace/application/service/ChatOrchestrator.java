package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.model.ChatRequest;
import com.example.sprinklr.marketplace.domain.model.Conversation;
import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.model.ToolResult;
import com.example.sprinklr.marketplace.domain.port.inbound.ChatUseCase;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import org.reactivestreams.FlowAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;

@Service
public class ChatOrchestrator implements ChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    /**
     * Number of prior messages loaded from MongoDB before each LLM call.
     * The orchestrator appends the new user message, then passes the full list to {@link LlmPort#complete}
     * so the model always sees recent conversation context (including when a prompt triggers MCP tools).
     */
    private static final int HISTORY_LIMIT = 5;
    private static final List<McpTool> NO_TOOLS = List.of();

    private final ChatHistoryPort chatHistoryPort;
    private final LlmPort llmPort;
    private final McpServerPort mcpServerPort;

    public ChatOrchestrator(
            ChatHistoryPort chatHistoryPort,
            LlmPort llmPort,
            McpServerPort mcpServerPort
    ) {
        this.chatHistoryPort = chatHistoryPort;
        this.llmPort = llmPort;
        this.mcpServerPort = mcpServerPort;
    }

    @Override
    //only method that is public and accessible from outside ,will be accessed by controller 
    public void streamChat(ChatRequest request, Flow.Subscriber<String> responseSubscriber) {
        Mono.fromRunnable(() -> runAgenticLoop(request, responseSubscriber))
                .subscribeOn(Schedulers.boundedElastic()) //"When I eventually run this task, run it on a thread from the boundedElastic pool."
                .subscribe(
                        unused -> {},
                        error -> signalError(responseSubscriber, error)
                );
    }

    private void runAgenticLoop(ChatRequest request, Flow.Subscriber<String> responseSubscriber) {
        try {
            String conversationId = resolveConversationId(request);
            List<Message> history = new ArrayList<>(
                    chatHistoryPort.findRecentMessages(conversationId, HISTORY_LIMIT)
            );

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

            // History (last HISTORY_LIMIT messages + new user turn) is always sent to the LLM,
            // even if the response will invoke MCP tools (e.g. user asks about Jira/GitLab).
            LlmResponse llmResponse = llmPort.complete(
                    new LlmRequest(request.prompt(), history, NO_TOOLS)
            );

            if (llmResponse.toolCalls().isEmpty()) {
                handleTextOnlyResponse(conversationId, llmResponse, responseSubscriber);
                return;
            }

            handleToolCalls(request, conversationId, history, llmResponse, responseSubscriber);
        } catch (Exception exception) {
            signalError(responseSubscriber, exception);
        }
    }

    private void handleTextOnlyResponse(
            String conversationId,
            LlmResponse llmResponse,
            Flow.Subscriber<String> responseSubscriber
    ) {//done to receive llm response in non blocking manner since llm generates text one at a time , everytime it geenrates onNext() called
       
        System.out.println("[Orchestrator] Handling text-only response");
        System.out.println("[Orchestrator] Response content: " + llmResponse.content());
        
        String content = llmResponse.content();
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

    private void handleToolCalls(
            ChatRequest request,
            String conversationId,
            List<Message> history,
            LlmResponse llmResponse,
            Flow.Subscriber<String> responseSubscriber
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

        List<McpInvocationResult> invocationResults = Flux.fromIterable(llmResponse.toolCalls())
                .flatMap(toolCall -> Mono.fromCallable(() -> mcpServerPort.invoke(toInvocation(toolCall)))
                        .subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .block();
        //basically multiple tools can be executed parallely in different threads , flatMap used for thatb

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

        //history just stores the tool messages after executiing the tool
        Flow.Subscriber<String> capturingSummarySubscriber = createCapturingSummarySubscriber(
                conversationId,
                responseSubscriber
        );
        llmPort.streamSummary(
                new LlmRequest(request.prompt(), history, NO_TOOLS),
                capturingSummarySubscriber
        );
    }

    private String resolveConversationId(ChatRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            // Check if conversation exists, if not create it
            Optional<Conversation> existing = chatHistoryPort.findConversationByIdAndUserId(
                    request.conversationId(),
                    request.userId()
            );
            if (existing.isPresent()) {
                return existing.get().id();
            }

            if (chatHistoryPort.findConversationById(request.conversationId()).isPresent()) {
                throw new IllegalStateException("Conversation does not belong to user");
            } else {
                // Create new conversation with the provided ID
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
        }

        // Create a new conversation with a generated ID
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

    //takes the LLM toolcall and converts it to MCPinvocation object to send it to MCP server
    private McpInvocation toInvocation(ToolCall toolCall) {
        int separatorIndex = toolCall.name().indexOf('.');
        if (separatorIndex > 0) {
            return new McpInvocation(
                    toolCall.name().substring(0, separatorIndex),
                    toolCall.name().substring(separatorIndex + 1),
                    toolCall.argumentsJson()
            );
        }
        //hmm slightly questionable
        return new McpInvocation("default", toolCall.name(), toolCall.argumentsJson());
    }

    private ToolResult toToolResult(McpInvocationResult result) {
        String content = result.success() ? result.content() : result.errorMessage();
        return new ToolResult(result.toolCallId(), content);
    }

    /**
     * Creates a wrapper subscriber that:
     * 1. Captures all streamed summary chunks
     * 2. Forwards them to the original responseSubscriber for UI display
     * 3. Saves the complete summary to database when streaming completes
     */
    private Flow.Subscriber<String> createCapturingSummarySubscriber(
            String conversationId,
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
                // Capture the chunk for saving later
                summaryBuffer.append(chunk);
                // Forward to original subscriber for UI display
                originalSubscriber.onNext(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("[Orchestrator] Summary stream error: " + throwable.getMessage());
                originalSubscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                System.out.println("[Orchestrator] Summary stream complete. Total content: " + summaryBuffer.length() + " chars");
                
                // Save the complete summary message to database
                String completeSummary = summaryBuffer.toString();
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
                    System.out.println("[Orchestrator] Saved assistant summary message: " + completeSummary.substring(0, Math.min(50, completeSummary.length())));
                }
                
                // Signal completion to original subscriber
                originalSubscriber.onComplete();
            }
        };
    }

    private void signalError(Flow.Subscriber<String> responseSubscriber, Throwable error) {
        try {
            if (LlmErrorFormatter.isVpnOrNetworkFailure(error)) {
                log.warn("[Orchestrator] LLM router unreachable — user likely not on VPN: {}",
                        error.getMessage());
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

    /**
     * Sends one SSE chunk and completes — used for text replies and user-visible error messages.
     */
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

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
