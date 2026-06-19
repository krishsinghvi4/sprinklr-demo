package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.AtlassianOAuthAuthStrategy;

import java.util.List;

/** Shared catalog fixtures for MCP extensibility tests. */
public final class McpCatalogTestFixtures {

    private McpCatalogTestFixtures() {
    }

    public static McpCatalogEntry jiraEntry() {
        McpAuthConfig authConfig = new McpAuthConfig(McpAuthKind.OAUTH, new McpOAuthCatalogConfig(
                "atlassian",
                "https://mcp.atlassian.com/.well-known/oauth-authorization-server",
                "https://mcp.atlassian.com/v1/mcp/authv2",
                "read:jira-work offline_access",
                true,
                true,
                true,
                true
        ));
        return new McpCatalogEntry(
                "atlassian-jira",
                "Jira",
                "desc",
                "https://mcp.atlassian.com/v1/mcp/authv2",
                "jira",
                AtlassianOAuthAuthStrategy.AUTH_TYPE,
                authConfig,
                McpConnectMethod.OAUTH_REDIRECT,
                List.of(),
                null
        );
    }
}
