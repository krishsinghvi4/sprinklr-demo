package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class McpCircuitBreakerFactoryTest {

    @Test
    void usesNamedBaseConfigFromRegistry() {
        CircuitBreakerConfig baseConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(45))
                .permittedNumberOfCallsInHalfOpenState(5)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
                java.util.Map.of(McpCircuitBreakerFactory.BASE_CONFIG_NAME, baseConfig)
        );
        McpCircuitBreakerFactory factory = new McpCircuitBreakerFactory(registry);

        CircuitBreaker breakerA = factory.forConnection("conn-a");
        CircuitBreaker breakerB = factory.forConnection("conn-b");

        assertEquals("mcp-conn-a", breakerA.getName());
        assertEquals(20, breakerA.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(60f, breakerA.getCircuitBreakerConfig().getFailureRateThreshold());
        assertNotSame(breakerA, breakerB);
    }
}
