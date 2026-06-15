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

    private final String systemPrompt;

    public LlmSystemPromptLoader(LlmProperties properties, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(properties.getSystemPromptPath());
        if (!resource.exists()) {
            throw new IllegalStateException("LLM system prompt not found at: " + properties.getSystemPromptPath());
        }
        try {
            this.systemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
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
}
