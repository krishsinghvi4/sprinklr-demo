package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.MCP.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.tool.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCacheKeyBuilder;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolDependencyPreflightGuardTest {

    @Mock
    private McpRegistryPort registryPort;

    private ToolDependencyPreflightGuard guard;

    @BeforeEach
    void setUp() {
        McpProperties properties = new McpProperties();
        properties.getToolSelection().setDependencyPreflightEnabled(true);
        guard = new ToolDependencyPreflightGuard(registryPort, properties);
    }

    @Test
    void blocksExecuteWhenOnlyCacheExists() {
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

    @Test
    void allowsExecuteWhenSampleCalledThisTurn() {
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

        McpInvocation invocation = new McpInvocation(
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_EXECUTE_TOOL,
                """
                        {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*","query":"{\\"query\\":{\\"match_all\\":{}}}"}
                        """,
                "call-1"
        );

        PreflightResult result = guard.validate(
                invocation,
                "user asked for audiences\n[tools-called-this-turn: red.red_sample_elasticsearch_query]"
        );

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
}
