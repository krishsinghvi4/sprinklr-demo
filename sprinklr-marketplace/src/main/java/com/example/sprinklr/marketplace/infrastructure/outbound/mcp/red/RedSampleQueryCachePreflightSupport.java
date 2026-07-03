package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.RedSampleQueryCachePort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Resolves fresh RED sample cache entries for execute tools and dependency preflight.
 */
@Component
public class RedSampleQueryCachePreflightSupport {

    private static final String RED_PREFIX = "red";

    private final RedSampleQueryCachePort cachePort;
    private final RedSampleQueryCacheKeyBuilder keyBuilder;
    private final McpConnectionRepository connectionRepository;
    private final McpProperties mcpProperties;

    public RedSampleQueryCachePreflightSupport(
            RedSampleQueryCachePort cachePort,
            RedSampleQueryCacheKeyBuilder keyBuilder,
            McpConnectionRepository connectionRepository,
            McpProperties mcpProperties
    ) {
        this.cachePort = cachePort;
        this.keyBuilder = keyBuilder;
        this.connectionRepository = connectionRepository;
        this.mcpProperties = mcpProperties;
    }

    public boolean isEnabled() {
        return mcpProperties.getRed() != null
                && mcpProperties.getRed().getSampleQueryCache() != null
                && mcpProperties.getRed().getSampleQueryCache().isEnabled();
    }

    /**
     * Returns cached sample content when an execute tool runs without a same-turn sample call.
     */
    public Optional<RedSampleQueryCacheHit> findCachedSampleForExecute(
            McpInvocation invocation,
            Set<String> calledToolNamesThisTurn
    ) {
        if (!isEnabled() || !keyBuilder.isExecuteTool(invocation.toolName())) {
            return Optional.empty();
        }
        String sampleTool = keyBuilder.sampleToolForExecute(invocation.toolName());
        String fullyQualifiedSample = keyBuilder.fullyQualifiedSampleTool(RED_PREFIX, sampleTool);
        if (calledToolNamesThisTurn.contains(fullyQualifiedSample)) {
            return Optional.empty();
        }
        return lookup(invocation, sampleTool, fullyQualifiedSample);
    }

    /**
     * Returns true when a missing sample prerequisite is satisfied by a fresh cache entry.
     */
    public boolean satisfiesMissingPrerequisite(
            McpInvocation invocation,
            String missingFullyQualifiedPrerequisite
    ) {
        if (!isEnabled() || !keyBuilder.isExecuteTool(invocation.toolName())) {
            return false;
        }
        String sampleTool = keyBuilder.sampleToolForExecute(invocation.toolName());
        if (sampleTool == null) {
            return false;
        }
        String expected = keyBuilder.fullyQualifiedSampleTool(RED_PREFIX, sampleTool);
        if (!expected.equals(missingFullyQualifiedPrerequisite)) {
            return false;
        }
        return lookup(invocation, sampleTool, expected).isPresent();
    }

    private Optional<RedSampleQueryCacheHit> lookup(
            McpInvocation invocation,
            String sampleTool,
            String fullyQualifiedSample
    ) {
        Optional<McpConnectionDocument> connection = connectionRepository.findById(invocation.serverId());
        if (connection.isEmpty() || !RED_PREFIX.equals(connection.get().serverIdPrefix())) {
            return Optional.empty();
        }
        return cachePort.find(
                connection.get().userId(),
                connection.get().id(),
                sampleTool,
                invocation.argumentsJson()
        ).map(content -> new RedSampleQueryCacheHit(fullyQualifiedSample, content));
    }
}
