package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogConnectProbeStrategyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void supportsCatalogEntriesWithConnectProbe() {
        var strategy = new CatalogConnectProbeStrategy(mock(StreamableHttpMcpClient.class));
        assertTrue(strategy.supports(McpCatalogTestFixtures.redEntry()));
    }

    @Test
    void validatesSuccessfulProbe() throws Exception {
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        var strategy = new CatalogConnectProbeStrategy(mcpClient);
        var entry = McpCatalogTestFixtures.redEntry();

        when(mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        assertDoesNotThrow(() -> strategy.validateLiveConnection(
                entry,
                entry.endpointUrl(),
                Map.of("Authorization", "Bearer token"),
                "session-1",
                "2025-03-26"
        ));
    }

    @Test
    void failsWithCatalogMessageWhenProbeReturnsError() throws Exception {
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        var strategy = new CatalogConnectProbeStrategy(mcpClient);
        var entry = McpCatalogTestFixtures.redEntry();

        when(mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(OBJECT_MAPPER.readTree("{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"unauthorized\"}]}"));

        McpConnectionException exception = assertThrows(McpConnectionException.class, () ->
                strategy.validateLiveConnection(
                        entry,
                        entry.endpointUrl(),
                        Map.of("Authorization", "Bearer token"),
                        "session-1",
                        "2025-03-26"
                ));
        assertEquals("Invalid RED token", exception.getUserMessage());
    }
}
