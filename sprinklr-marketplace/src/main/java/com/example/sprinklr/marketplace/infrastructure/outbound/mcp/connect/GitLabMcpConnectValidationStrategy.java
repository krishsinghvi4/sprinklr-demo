package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.GitLabPrivateTokenAuthStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * GitLab MCP defers PAT validation until GitLab API calls. Probe with a lightweight tool
 * so invalid tokens fail at connect time instead of appearing connected.
 */
@Component
public class GitLabMcpConnectValidationStrategy implements McpConnectValidationStrategy {

    private static final Logger log = LoggerFactory.getLogger(GitLabMcpConnectValidationStrategy.class);
    private static final String PROBE_TOOL = "list_namespaces";
    private static final String PROBE_ARGS = "{\"per_page\":1}";

    private final StreamableHttpMcpClient mcpClient;

    public GitLabMcpConnectValidationStrategy(StreamableHttpMcpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return GitLabPrivateTokenAuthStrategy.AUTH_TYPE.equals(entry.authType());
    }

    @Override
    public void validateLiveConnection(
            McpCatalogEntry entry,
            String endpointUrl,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion
    ) {
        log.info("[MCP] Validating GitLab token via probe tool={} catalogServerId={}",
                PROBE_TOOL, entry.id());
        JsonNode result;
        try {
            result = mcpClient.callTool(
                    endpointUrl,
                    authHeaders,
                    sessionId,
                    protocolVersion,
                    PROBE_TOOL,
                    PROBE_ARGS
            );
        } catch (McpConnectionException exception) {
            throw new McpConnectionException(
                    "Invalid GitLab personal access token — verify the token has api scope and is not expired.",
                    exception.getMessage()
            );
        }

        Optional<String> toolError = McpToolResultInspector.extractError(result);
        if (toolError.isPresent()) {
            throw new McpConnectionException(
                    "Invalid GitLab personal access token — verify the token has api scope and is not expired.",
                    "GitLab connect probe failed: " + toolError.get()
            );
        }
    }
}
