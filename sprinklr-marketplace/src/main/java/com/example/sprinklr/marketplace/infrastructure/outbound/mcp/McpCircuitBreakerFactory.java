package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

@Component
public class McpCircuitBreakerFactory {

    /** Base Resilience4j config name; tuned via {@code resilience4j.circuitbreaker.configs.mcpDefault.*}. */
    static final String BASE_CONFIG_NAME = "mcpDefault";

    private final CircuitBreakerRegistry registry;

    public McpCircuitBreakerFactory(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    public CircuitBreaker forConnection(String connectionId) {
        String name = "mcp-" + connectionId;
        return registry.circuitBreaker(name, BASE_CONFIG_NAME);
    }
}
