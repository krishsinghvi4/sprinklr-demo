package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolResultInspectorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void detectsAuthErrorFlag() throws Exception {
        var result = OBJECT_MAPPER.readTree("""
                {"isError":true,"content":[{"type":"text","text":"401 Unauthorized"}]}
                """);
        assertTrue(McpToolResultInspector.extractError(result).isPresent());
    }

    @Test
    void acceptsSuccessfulEmptyList() throws Exception {
        var result = OBJECT_MAPPER.readTree("""
                {"content":[{"type":"text","text":"[]"}]}
                """);
        assertFalse(McpToolResultInspector.extractError(result).isPresent());
    }
}
