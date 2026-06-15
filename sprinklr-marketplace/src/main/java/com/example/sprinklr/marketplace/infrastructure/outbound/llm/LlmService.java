package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.config.LlmProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.ChatCompletionRequest;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiMessage;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiTool;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Internal orchestration layer for LLM chat-completion calls.
 * <p>
 * Industry pattern: the domain {@link com.example.sprinklr.marketplace.domain.port.outbound.LlmPort}
 * adapter stays thin; this service owns request assembly, HTTP invocation, parsing, logging, and
 * resilience. Adding streaming ({@code streamSummary}) or swapping router endpoints later
 * changes this class and {@link ChatCompletionClient}, not {@link com.example.sprinklr.marketplace.application.service.ChatOrchestrator}.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final LlmProperties properties;
    private final LlmMessageMapper messageMapper;
    private final LlmToolMapper toolMapper;
    private final ChatCompletionClient completionClient;
    private final LlmResponseParser responseParser;
    private final CircuitBreaker circuitBreaker;

    public LlmService(
            LlmProperties properties,
            LlmMessageMapper messageMapper,
            LlmToolMapper toolMapper,
            ChatCompletionClient completionClient,
            LlmResponseParser responseParser,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.properties = properties;
        this.messageMapper = messageMapper;
        this.toolMapper = toolMapper;
        this.completionClient = completionClient;
        this.responseParser = responseParser;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmRouter");
    }

    /**
     * Non-streaming completion: used for the decision pass (text vs tool_calls) and future summary prep.
     * <p>
     * Conversation history from MongoDB is always included in the router payload — even when the user's
     * prompt will trigger MCP tools — so the model can resolve references like "that ticket" or "the MR".
     *
     * @param command history, optional whitelisted tools, and whether to include tool rows in history
     */
    public LlmCompletionResult complete(LlmCompletionCommand command) {
        return CircuitBreaker.decorateSupplier(circuitBreaker, () -> executeCompletion(command)).get();
    }

    private LlmCompletionResult executeCompletion(LlmCompletionCommand command) {
        List<McpTool> tools = command.tools();
        boolean includeFullToolHistory = command.includeFullToolHistory();

        List<LlmApiMessage> apiMessages = messageMapper.toApiMessages(command.history(), includeFullToolHistory);
        List<LlmApiTool> apiTools = toolMapper.toApiTools(tools);
        String toolChoice = toolMapper.resolveToolChoice(tools);

        int rawHistorySize = command.history().size();
        int mappedHistoryCount = messageMapper.countMappedHistoryMessages(command.history(), includeFullToolHistory);

        log.info("[LLM] Starting completion model={} tools={} historyRaw={} historyMapped={} fullToolHistory={}",
                properties.getModel(), tools.size(), rawHistorySize, mappedHistoryCount, includeFullToolHistory);

        ChatCompletionRequest request = buildRequest(apiMessages, apiTools, toolChoice);

        long startMs = System.currentTimeMillis();
        String rawBody = completionClient.postCompletion(request);
        LlmCompletionResult result = responseParser.parse(rawBody);
        long elapsedMs = System.currentTimeMillis() - startMs;

        String responseType = result.toolCalls().isEmpty() ? "text" : "tool_calls";
        log.info("[LLM] Completion finished in {}ms responseType={} toolCallCount={}",
                elapsedMs, responseType, result.toolCalls().size());

        if (log.isDebugEnabled()) {
            log.debug("[LLM] Response preview: {}",
                    result.content() != null ? truncate(result.content(), 200) : "null");
        }

        return result;
    }

    private ChatCompletionRequest buildRequest(
            List<LlmApiMessage> messages,
            List<LlmApiTool> tools,
            String toolChoice
    ) {
        Map<String, String> trackingParams = Map.of(
                "feature", properties.getTrackingParams().getFeature()
        );

        return new ChatCompletionRequest(
                properties.getModel(),
                properties.getClientIdentifier(),
                properties.getPartnerId(),
                messages,
                properties.getProvider(),
                tools.isEmpty() ? List.of() : tools,
                toolChoice,
                properties.getTemperature(),
                properties.getMaxCompletionTokens(),
                trackingParams
        );
    }

    private static String truncate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }
}
