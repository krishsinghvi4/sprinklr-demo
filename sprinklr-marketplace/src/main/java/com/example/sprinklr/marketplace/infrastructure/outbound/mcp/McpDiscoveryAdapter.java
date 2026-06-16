package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.port.outbound.McpDiscoveryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class McpDiscoveryAdapter implements McpDiscoveryPort {

    private static final Logger log = LoggerFactory.getLogger(McpDiscoveryAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StreamableHttpMcpClient mcpClient;

    public McpDiscoveryAdapter(StreamableHttpMcpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public McpDiscoveryResult discover(
            String endpointUrl,
            Map<String, String> authHeaders,
            String serverIdPrefix,
            String connectionId
    ) {
        log.info("[MCP] Discovering tools endpoint={} prefix={} connectionId={}",
                endpointUrl, serverIdPrefix, connectionId);

        StreamableHttpMcpClient.McpSession session = mcpClient.initialize(endpointUrl, authHeaders);
        List<McpTool> allTools = new ArrayList<>();
        String cursor = null;

        do {
            JsonNode result = mcpClient.listTools(endpointUrl, authHeaders, session);
            JsonNode toolsNode = result.path("tools");
            if (toolsNode.isArray()) {
                for (JsonNode toolNode : toolsNode) {
                    allTools.add(toMcpTool(toolNode, serverIdPrefix, connectionId));
                }
            }
            cursor = result.path("nextCursor").asText(null);
            if (cursor != null && !cursor.isBlank()) {
                log.debug("[MCP] Paginating tools/list cursor={}", cursor);
            }
        } while (cursor != null && !cursor.isBlank());

        log.info("[MCP] Discovered {} tools for connectionId={}", allTools.size(), connectionId);
        return new McpDiscoveryResult(session.sessionId(), session.protocolVersion(), allTools);
    }

    private McpTool toMcpTool(JsonNode toolNode, String serverIdPrefix, String connectionId) {
        String rawName = toolNode.path("name").asText();
        String description = toolNode.path("description").asText("No description");
        JsonNode inputSchema = toolNode.path("inputSchema");
        String schemaJson;
        try {
            schemaJson = OBJECT_MAPPER.writeValueAsString(
                    inputSchema.isMissingNode() ? OBJECT_MAPPER.createObjectNode() : inputSchema
            );
        } catch (Exception exception) {
            schemaJson = "{}";
        }

        return new McpTool(
                serverIdPrefix + "." + rawName,
                description,
                connectionId,
                schemaJson
        );
    }
}
