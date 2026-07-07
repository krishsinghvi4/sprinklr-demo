package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.port.outbound.RedSampleQueryCachePort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolInvocationContext;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedSampleQueryCacheLocalToolTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private RedSampleQueryCachePort cachePort;

    @Mock
    private StreamableHttpMcpClient mcpClient;

    private RedSampleQueryCacheLocalTool localTool;
    private McpCatalogEntry redEntry;
    private McpLocalToolInvocationContext context;

    @BeforeEach
    void setUp() {
        localTool = new RedSampleQueryCacheLocalTool(
                cachePort,
                new RedSampleQueryCacheKeyBuilder(),
                mcpClient
        );
        redEntry = McpCatalogTestFixtures.redEntry();
        McpConnectionDocument connection = new McpConnectionDocument(
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
                null,
                null
        );
        context = new McpLocalToolInvocationContext(
                redEntry,
                connection,
                Map.of("apiToken", "token"),
                Map.of("Authorization", "Bearer token"),
                new StreamableHttpMcpClient.McpSession("session-1", "2025-03-26"),
                """
                        {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                        """
        );
    }

    @Test
    void returnsCachedContentWithoutCallingMcp() throws Exception {
        when(cachePort.find(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("{\"hits\":{\"hits\":[]}}"));

        String result = localTool.invoke(
                redEntry,
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                context
        );

        assertEquals("{\"hits\":{\"hits\":[]}}", result);
        verify(mcpClient, never()).callTool(anyString(), any(), anyString(), anyString(), anyString(), anyString());
    }

    private static final String SAMPLE_JSON =
            "{\"hits\":{\"hits\":[{\"_id\":\"x\",\"_source\":{\"entityType\":\"AD_SET\",\"accountId\":1}}]}}";

    @Test
    void appendsFieldPathIndexOnCacheHit() {
        when(cachePort.find(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(SAMPLE_JSON));

        String result = localTool.invoke(
                redEntry,
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                context
        );

        assertTrue(result.contains("### Filter field paths"));
        assertTrue(result.contains("entityType (string)"));
        assertTrue(result.startsWith(SAMPLE_JSON));
    }

    @Test
    void callsMcpAndSavesOnCacheMiss() throws Exception {
        when(cachePort.find(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode content = OBJECT_MAPPER.createArrayNode();
        ObjectNode textItem = OBJECT_MAPPER.createObjectNode();
        textItem.put("text", SAMPLE_JSON);
        content.add(textItem);
        resultNode.set("content", content);

        when(mcpClient.callTool(
                eq("http://127.0.0.1:3344/mcp"),
                any(),
                eq("session-1"),
                eq("2025-03-26"),
                eq(RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL),
                anyString()
        )).thenReturn(resultNode);

        String result = localTool.invoke(
                redEntry,
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                context
        );

        assertTrue(result.startsWith(SAMPLE_JSON));
        assertTrue(result.contains("### Filter field paths"));
        assertTrue(result.contains("entityType (string)"));

        ArgumentCaptor<String> savedContent = ArgumentCaptor.forClass(String.class);
        verify(cachePort).save(
                eq("user-1"),
                eq("conn-1"),
                eq(RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL),
                anyString(),
                savedContent.capture()
        );
        assertEquals(SAMPLE_JSON, savedContent.getValue());
        assertFalse(savedContent.getValue().contains("### Filter field paths"));
    }
}
