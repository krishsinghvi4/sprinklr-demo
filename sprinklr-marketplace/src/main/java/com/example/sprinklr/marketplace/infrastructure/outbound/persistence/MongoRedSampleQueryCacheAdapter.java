package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.port.outbound.RedSampleQueryCachePort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCacheKey;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCacheKeyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Mongo-backed cache for full RED sample query results.
 */
@Component
public class MongoRedSampleQueryCacheAdapter implements RedSampleQueryCachePort {

    private static final Logger log = LoggerFactory.getLogger(MongoRedSampleQueryCacheAdapter.class);

    private final RedSampleQueryCacheRepository repository;
    private final RedSampleQueryCacheKeyBuilder keyBuilder;
    private final McpProperties mcpProperties;

    public MongoRedSampleQueryCacheAdapter(
            RedSampleQueryCacheRepository repository,
            RedSampleQueryCacheKeyBuilder keyBuilder,
            McpProperties mcpProperties
    ) {
        this.repository = repository;
        this.keyBuilder = keyBuilder;
        this.mcpProperties = mcpProperties;
    }

    @Override
    public Optional<String> find(String userId, String connectionId, String toolName, String argumentsJson) {
        if (!isEnabled() || !keyBuilder.isSampleTool(toolName)) {
            return Optional.empty();
        }
        RedSampleQueryCacheKey key = keyBuilder.build(userId, connectionId, toolName, argumentsJson);
        return repository.findById(key.id())
                .filter(document -> userId.equals(document.userId()))
                .filter(document -> connectionId.equals(document.connectionId()))
                .filter(document -> document.expiresAt() != null && document.expiresAt().isAfter(Instant.now()))
                .map(RedSampleQueryCacheDocument::resultContent)
                .map(content -> {
                    log.info("[RedSampleCache] Hit userId={} connectionId={} tool={} key={}",
                            userId, connectionId, toolName, key.id());
                    return content;
                });
    }

    @Override
    public void save(
            String userId,
            String connectionId,
            String toolName,
            String argumentsJson,
            String resultContent
    ) {
        if (!isEnabled() || !keyBuilder.isSampleTool(toolName)) {
            return;
        }
        if (resultContent == null || resultContent.isBlank()) {
            return;
        }
        RedSampleQueryCacheKey key = keyBuilder.build(userId, connectionId, toolName, argumentsJson);
        Instant expiresAt = Instant.now().plus(cacheTtlHours(), ChronoUnit.HOURS);
        repository.save(new RedSampleQueryCacheDocument(
                key.id(),
                userId,
                connectionId,
                toolName,
                key.scopeArgsJson(),
                resultContent,
                expiresAt
        ));
        log.info("[RedSampleCache] Saved userId={} connectionId={} tool={} key={} expiresAt={} resultLen={}",
                userId, connectionId, toolName, key.id(), expiresAt, resultContent.length());
    }

    private boolean isEnabled() {
        return mcpProperties.getRed() != null
                && mcpProperties.getRed().getSampleQueryCache() != null
                && mcpProperties.getRed().getSampleQueryCache().isEnabled();
    }

    private int cacheTtlHours() {
        int configured = mcpProperties.getRed().getSampleQueryCache().getTtlHours();
        return configured > 0 ? configured : 24;
    }
}
