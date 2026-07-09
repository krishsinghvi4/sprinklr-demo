package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP;

import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectProbeConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * REST DTO for connectProbe — accepts {@code arguments} as a JSON object (same shape as mcp-catalog.json).
 */
public record ConnectProbeRequestDto(
        String tool,
        Map<String, Object> arguments,
        String failureMessage
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public McpConnectProbeConfig toDomain() {
        String argumentsJson = serializeArguments(arguments);
        return new McpConnectProbeConfig(tool, argumentsJson, failureMessage);
    }

    private static String serializeArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(arguments);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("connectProbe.arguments must be a valid JSON object", exception);
        }
    }
}
