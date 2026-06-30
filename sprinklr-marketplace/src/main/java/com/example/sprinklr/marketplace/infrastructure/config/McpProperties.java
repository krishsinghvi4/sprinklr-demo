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
    /** Max MCP HTTP response body size buffered in memory (default 5 MB). */
    private int maxResponseBytes = 5_242_880;
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

    /** Progressive tool-context (router + dependency-graph expansion) settings. */
    private ToolSelection toolSelection = new ToolSelection();

    /**
     * Tunables for the two-stage tool selection pipeline. All values are configurable via
     * {@code app.mcp.tool-selection.*} in application.properties.
     */
    @Data
    public static class ToolSelection {
        /** Master switch. When false, the orchestrator sends every active tool to the LLM (legacy behavior). */
        private boolean enabled = true;
        /** Hard cap on the number of full-schema tools handed to the agent LLM per turn. */
        private int maxTools = 15;
        /** Soft cap on how many primary tools the lightweight router is asked to pick. */
        private int routerMaxPrimaryTools = 8;
        /** Recent conversation turns passed to the router for context. */
        private int routerHistoryTurns = 2;
        /** When true, generate the dependency graph via the LLM at connect time. */
        private boolean generateGraphOnConnect = true;
        /** When true, the generic dependency preflight guard can block out-of-order tool calls (opt-in safety net). */
        private boolean dependencyPreflightEnabled = true;
        /** TTL (hours) for cross-turn continuation state. */
        private int continuationTtlHours = 24;
    }
}
