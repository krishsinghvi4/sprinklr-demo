package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.ChatCompletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.HttpHeaders;

/**
 * Thin HTTP transport for the Sprinklr LLM router — POST JSON, return raw response body.
 * <p>
 * No domain mapping here: {@link LlmService} owns orchestration and {@link LlmResponseParser}
 * owns deserialization. This class only handles wire protocol and HTTP errors.
 */
/**
 * HTTP client for the Sprinklr LLM router.
 * Handles retries for transient failures and surfaces HTTP error details.
 */
@Component
public class ChatCompletionClient {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionClient.class);

    /**
     * Retry once on stale pooled connections (e.g. user idle in chat, ALB closed the socket).
     */
    private static final int MAX_ATTEMPTS = 2;
    private static final long DEFAULT_RETRY_AFTER_MS = 1_000L;

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
                int status = e.getStatusCode().value();
                if (status == 429 && attempt < MAX_ATTEMPTS) {
                    long retryAfterMs = retryAfterMillis(e.getHeaders());
                    log.warn("[LLM-CLIENT] Rate limited (HTTP 429). Waiting {}ms before retry", retryAfterMs);
                    sleep(retryAfterMs);
                    continue;
                }
                String body = truncate(e.getResponseBodyAsString(), 500);
                log.error("[LLM-CLIENT] HTTP {} from router: {}", status, body);
                throw new LlmClientException("LLM router HTTP " + status + ": " + body, e);
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

    /**
     * Executes the HTTP request without retry logic.
     */
    private String executePost(ChatCompletionRequest request) {
        return llmWebClient.post()
                .uri(properties.getCompletionPath())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * Normalizes errors into LlmClientException with context for the caller.
     */
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

    private static long retryAfterMillis(HttpHeaders headers) {
        if (headers == null) {
            return DEFAULT_RETRY_AFTER_MS;
        }
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return DEFAULT_RETRY_AFTER_MS;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.max(0, seconds) * 1000L;
        } catch (NumberFormatException ignored) {
            return DEFAULT_RETRY_AFTER_MS;
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
