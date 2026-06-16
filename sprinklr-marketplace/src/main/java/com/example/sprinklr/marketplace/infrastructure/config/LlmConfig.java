package com.example.sprinklr.marketplace.infrastructure.config;

import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.SprinklrLlmRouterAdapter;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.StubLlmAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the correct {@link LlmPort} implementation based on {@link LlmProperties#isStubEnabled()}.
 * <p>
 * Using explicit {@link Bean} registration (instead of {@code @Component} on adapters) avoids
 * duplicate beans and makes the stub vs real router switch a single configuration flag.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "app.llm.stub-enabled", havingValue = "true", matchIfMissing = true)
    public LlmPort stubLlmPort() {
        return new StubLlmAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.llm.stub-enabled", havingValue = "false")
    public LlmPort sprinklrLlmRouterPort(
            com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmService llmService,
            LlmSystemPromptLoader systemPromptLoader
    ) {
        return new SprinklrLlmRouterAdapter(llmService, systemPromptLoader);
    }
}
