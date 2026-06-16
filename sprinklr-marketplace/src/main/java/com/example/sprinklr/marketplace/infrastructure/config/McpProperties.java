package com.example.sprinklr.marketplace.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 60_000;
    private int maxAgenticIterations = 5;
    private int maxToolCallsPerTurn = 10;
    private String encryptionKey = "";
    private String catalogPath = "classpath:mcp/mcp-catalog.json";
    private String oauthRedirectUri = "http://localhost:5173/oauth/callback";
    private String oauthMetadataUrl = "https://mcp.atlassian.com/.well-known/oauth-authorization-server";
    private String oauthResource = "https://mcp.atlassian.com/v1/mcp/authv2";
    private String oauthScopes =
            "read:jira-work write:jira-work search:jira-work read:me read:account offline_access";
    private long oauthStateTtlSeconds = 600;
    private String oauthSuccessRedirectUrl = "http://localhost:5173/profile?oauth=success";
    private String oauthErrorRedirectUrl = "http://localhost:5173/profile?oauth=error";
}
