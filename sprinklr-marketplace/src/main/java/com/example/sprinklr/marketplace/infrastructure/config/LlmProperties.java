package com.example.sprinklr.marketplace.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Sprinklr IntuitionX LLM router.
 * <p>
 * Model, provider, temperature, and token limits are property-driven so you can
 * change LLM behavior without code changes. The system prompt file path is here;
 * actual prompt text is loaded by {@link LlmSystemPromptLoader}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.llm")// basically puts all these values in the application.properties file
public class LlmProperties {

    /** When true, {@link com.example.sprinklr.marketplace.infrastructure.outbound.llm.StubLlmAdapter} is used instead of the real router. */
    private boolean stubEnabled = true;

    private String baseUrl;
    private String completionPath;
    /** AWS/session cookie for router auth — supply via LLM_ROUTER_COOKIE env var; never log this value. */
    private String cookie;
    private String model;
    private String clientIdentifier;
    private int partnerId;
    private String provider;
    private double temperature;
    private int maxCompletionTokens;
    private TrackingParams trackingParams = new TrackingParams();
    private int connectTimeoutMs = 30000;
    private int readTimeoutMs = 120000;
    private String systemPromptPath = "classpath:llm/system-prompt.txt";

    @Getter
    @Setter
    public static class TrackingParams {
        private String feature;
    }
}
