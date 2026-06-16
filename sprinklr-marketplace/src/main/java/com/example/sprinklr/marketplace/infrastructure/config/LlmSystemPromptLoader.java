package com.example.sprinklr.marketplace.infrastructure.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the LLM system prompt from the classpath file configured in {@link LlmProperties#getSystemPromptPath()}.
 * Separated from {@link LlmProperties} because Spring Boot binds configuration properties without constructor injection.
 */
@Component
public class LlmSystemPromptLoader {

    private static final String SUMMARY_PROMPT_PATH = "classpath:llm/summary-prompt.txt";

    private final String systemPrompt;
    private final String summaryPrompt;

    public LlmSystemPromptLoader(LlmProperties properties, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(properties.getSystemPromptPath());
        if (!resource.exists()) {
            throw new IllegalStateException("LLM system prompt not found at: " + properties.getSystemPromptPath());
        }
        try {
            this.systemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
            this.summaryPrompt = loadOptionalPrompt(resourceLoader, SUMMARY_PROMPT_PATH, systemPrompt);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read LLM system prompt from: " + properties.getSystemPromptPath(), e);
        }

        if (!properties.isStubEnabled() && (properties.getCookie() == null || properties.getCookie().isBlank())) {
            throw new IllegalStateException(
                    "app.llm.cookie (LLM_ROUTER_COOKIE) is required when app.llm.stub-enabled=false");
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getSummaryPrompt() {
        return summaryPrompt;
    }

    private static String loadOptionalPrompt(ResourceLoader resourceLoader, String path, String fallback)
            throws IOException {
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            return fallback;
        }
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
    }
}
