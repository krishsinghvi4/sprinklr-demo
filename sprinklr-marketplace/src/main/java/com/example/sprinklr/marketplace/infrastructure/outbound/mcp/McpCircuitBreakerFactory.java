package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class McpCircuitBreakerFactory {

    private final CircuitBreakerRegistry registry;

    public McpCircuitBreakerFactory(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    public CircuitBreaker forConnection(String connectionId) {
        String name = "mcp-" + connectionId;
        return registry.circuitBreaker(name, () -> CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
    }
}
