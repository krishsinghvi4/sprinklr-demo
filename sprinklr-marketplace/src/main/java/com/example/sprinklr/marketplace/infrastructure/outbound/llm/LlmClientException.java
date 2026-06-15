package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

/**
 * Wraps failures from the Sprinklr LLM router (HTTP errors, timeouts, malformed JSON).
 * Network/connectivity failures usually mean the Sprinklr VPN is not connected.
 * The orchestrator surfaces {@link LlmErrorFormatter#VPN_REQUIRED_MESSAGE} to the chat UI.
 */
public class LlmClientException extends RuntimeException {

    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
