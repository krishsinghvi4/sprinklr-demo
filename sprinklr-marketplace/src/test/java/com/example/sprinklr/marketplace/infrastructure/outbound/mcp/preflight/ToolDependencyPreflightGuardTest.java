package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.RedSampleQueryCachePort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCacheKeyBuilder;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCachePreflightSupport;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolDependencyPreflightGuardTest {

    @Mock
    private McpRegistryPort registryPort;

    @Mock
    private RedSampleQueryCachePort cachePort;

    @Mock
    private McpConnectionRepository connectionRepository;

    private ToolDependencyPreflightGuard guard;

    @BeforeEach
    void setUp() {
        McpProperties properties = new McpProperties();
        properties.getToolSelection().setDependencyPreflightEnabled(true);
        RedSampleQueryCachePreflightSupport cacheSupport = new RedSampleQueryCachePreflightSupport(
                cachePort,
                new RedSampleQueryCacheKeyBuilder(),
                connectionRepository,
                properties
        );
        guard = new ToolDependencyPreflightGuard(registryPort, properties, cacheSupport);
    }

    @Test
    void allowsExecuteWhenFreshSampleCacheExists() {
        ToolDependencyGraph graph = new ToolDependencyGraph(
                "red",
                Map.of(
                        "red.red_execute_elastic_search_query",
                        List.of("red.red_sample_elasticsearch_query")
                ),
                "fp",
                Instant.now(),
                DependencyGraphStatus.READY
        );
        when(registryPort.findDependencyGraphByConnectionId("conn-1")).thenReturn(Optional.of(graph));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection()));
        when(cachePort.find(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("cached-sample"));

        McpInvocation invocation = new McpInvocation(
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_EXECUTE_TOOL,
                """
                        {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*","query":"{\\"query\\":{\\"match_all\\":{}}}"}
                        """,
                "call-1"
        );

        PreflightResult result = guard.validate(invocation, "user asked for audiences");

        assertTrue(result.allowed());
    }

    @Test
    void blocksExecuteWhenSampleCacheMissing() {
        ToolDependencyGraph graph = new ToolDependencyGraph(
                "red",
                Map.of(
                        "red.red_execute_elastic_search_query",
                        List.of("red.red_sample_elasticsearch_query")
                ),
                "fp",
                Instant.now(),
                DependencyGraphStatus.READY
        );
        when(registryPort.findDependencyGraphByConnectionId("conn-1")).thenReturn(Optional.of(graph));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection()));
        when(cachePort.find(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        McpInvocation invocation = new McpInvocation(
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_EXECUTE_TOOL,
                """
                        {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*","query":"{\\"query\\":{\\"match_all\\":{}}}"}
                        """,
                "call-1"
        );

        PreflightResult result = guard.validate(invocation, "user asked for audiences");

        assertFalse(result.allowed());
    }

    private static McpConnectionDocument connection() {
        return new McpConnectionDocument(
                "conn-1",
                "user-1",
                "red-mcp",
                "red",
                "enc",
                "session-1",
                "2025-03-26",
                "ACTIVE",
                List.of(),
                Instant.now(),
                null,
                null,
                null
        );
    }
}
