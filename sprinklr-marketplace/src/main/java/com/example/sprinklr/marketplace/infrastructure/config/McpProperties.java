package com.example.sprinklr.marketplace.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP configuration values used for discovery, OAuth, and agentic limits.
 */
@Data
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 60_000;
    private int maxAgenticIterations = 10;
    private int maxToolCallsPerTurn = 15;
    private String encryptionKey = "";
    // Resource path to the MCP marketplace catalog.
    private String catalogPath = "classpath:mcp/mcp-catalog.json";
    /** App-wide OAuth redirect URI registered with all OAuth MCP issuers. */
    private String oauthRedirectUri = "http://localhost:5173/oauth/callback";
    private long oauthStateTtlSeconds = 600;
    private String oauthSuccessRedirectUrl = "http://localhost:5173/profile?oauth=success";
    private String oauthErrorRedirectUrl = "http://localhost:5173/profile?oauth=error";
    /** Streamable HTTP endpoint for the zereight GitLab MCP server (docker maps 3333 -> 3002). */
    private String gitlabMcpEndpointUrl = "http://127.0.0.1:3333/mcp";
    /** GitLab MCP auth header mode: private-token (default) or bearer. */
    private String gitlabAuthHeaderMode = "private-token";
}
