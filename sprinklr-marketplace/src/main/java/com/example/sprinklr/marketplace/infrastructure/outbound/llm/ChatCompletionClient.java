package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.infrastructure.config.LlmProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.ChatCompletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Thin HTTP transport for the Sprinklr LLM router — POST JSON, return raw response body.
 * <p>
 * No domain mapping here: {@link LlmService} owns orchestration and {@link LlmResponseParser}
 * owns deserialization. This class only handles wire protocol and HTTP errors.
 */
@Component
public class ChatCompletionClient {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionClient.class);

    /**
     * Retry once on stale pooled connections (e.g. user idle in chat, ALB closed the socket).
     */
    private static final int MAX_ATTEMPTS = 2;

    private final WebClient llmWebClient;
    private final LlmProperties properties;

    public ChatCompletionClient(
            @Qualifier("llmWebClient") WebClient llmWebClient,
            LlmProperties properties
    ) {
        this.llmWebClient = llmWebClient;
        this.properties = properties;
    }

    /**
     * POST to {@code /chat-completion} and block until the full response arrives.
     * Called from {@link LlmService} on a boundedElastic thread (orchestrator offloads blocking work).
     */
    public String postCompletion(ChatCompletionRequest request) {
        String path = properties.getCompletionPath();
        log.debug("[LLM-CLIENT] POST {} model={} messages={} tools={}",
                path, request.model(), request.messages().size(),
                request.tools() != null ? request.tools().size() : 0);

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return executePost(request);
            } catch (WebClientResponseException e) {
                String body = truncate(e.getResponseBodyAsString(), 500);
                log.error("[LLM-CLIENT] HTTP {} from router: {}", e.getStatusCode().value(), body);
                throw new LlmClientException("LLM router HTTP " + e.getStatusCode().value() + ": " + body, e);
            } catch (Exception e) {
                lastFailure = e;
                if (attempt < MAX_ATTEMPTS && LlmErrorFormatter.isTransientNetworkFailure(e)) {
                    log.warn("[LLM-CLIENT] Transient network error on attempt {} (stale connection?): {} — retrying",
                            attempt, e.getMessage());
                    continue;
                }
                throw toClientException(e);
            }
        }

        throw toClientException(lastFailure);
    }

    private String executePost(ChatCompletionRequest request) {
        return llmWebClient.post()
                .uri(properties.getCompletionPath())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private LlmClientException toClientException(Exception e) {
        if (LlmErrorFormatter.isTransientNetworkFailure(e)) {
            log.warn("[LLM-CLIENT] Connection interrupted after {} attempts: {}", MAX_ATTEMPTS, e.getMessage());
            return new LlmClientException("LLM router connection interrupted: " + e.getMessage(), e);
        }
        if (LlmErrorFormatter.isVpnOrNetworkFailure(e)) {
            log.error("[LLM-CLIENT] Router unreachable (VPN required): {}", e.getMessage());
            return new LlmClientException(
                    "LLM router unreachable — connect to Sprinklr VPN: " + e.getMessage(), e);
        }
        log.error("[LLM-CLIENT] Request failed: {}", e.getMessage());
        return new LlmClientException("LLM router request failed: " + e.getMessage(), e);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }
}
