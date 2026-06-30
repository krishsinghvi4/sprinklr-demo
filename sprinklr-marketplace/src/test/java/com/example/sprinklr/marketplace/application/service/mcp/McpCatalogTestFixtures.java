package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.McpConnectProbeConfig;
import com.example.sprinklr.marketplace.domain.model.McpCredentialAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpCredentialHeaderMode;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.domain.model.McpToolSelectionConfig;

import java.util.List;

/** Shared catalog fixtures for MCP extensibility tests. */
public final class McpCatalogTestFixtures {

    private McpCatalogTestFixtures() {
    }

    public static McpAuthConfig oauthAuthConfig() {
        return new McpAuthConfig(McpAuthKind.OAUTH, new McpOAuthCatalogConfig(
                "atlassian",
                "https://mcp.atlassian.com/.well-known/oauth-authorization-server",
                "https://mcp.atlassian.com/v1/mcp/authv2",
                "read:jira-work offline_access",
                true,
                true,
                true,
                true
        ));
    }

    public static McpAuthConfig bearerCredentialAuthConfig() {
        return new McpAuthConfig(
                McpAuthKind.CREDENTIALS,
                null,
                new McpCredentialAuthConfig("apiToken", McpCredentialHeaderMode.BEARER, null, null)
        );
    }

    public static McpAuthConfig privateTokenCredentialAuthConfig() {
        return new McpAuthConfig(
                McpAuthKind.CREDENTIALS,
                null,
                new McpCredentialAuthConfig("apiToken", McpCredentialHeaderMode.PRIVATE_TOKEN, null, null)
        );
    }

    public static McpCatalogEntry jiraEntry() {
        return new McpCatalogEntry(
                "atlassian-jira",
                "Jira",
                "desc",
                "https://mcp.atlassian.com/v1/mcp/authv2",
                "jira",
                "OAUTH",
                oauthAuthConfig(),
                McpConnectMethod.OAUTH_REDIRECT,
                List.of(),
                null,
                null,
                new McpToolSelectionConfig(List.of(
                        "jira.getAccessibleAtlassianResources",
                        "jira.getJiraProjectIssueTypesMetadata",
                        "jira.getJiraIssueTypeMetaWithFields"
                ))
        );
    }

    public static McpCatalogEntry gitlabEntry() {
        return new McpCatalogEntry(
                "gitlab-mcp",
                "GitLab",
                "desc",
                "http://127.0.0.1:3333/mcp",
                "gitlab",
                "CREDENTIALS",
                privateTokenCredentialAuthConfig(),
                McpConnectMethod.CREDENTIAL_FORM,
                List.of(),
                null,
                new McpConnectProbeConfig("list_namespaces", "{\"per_page\":1}", "Invalid GitLab token"),
                null
        );
    }

    public static McpCatalogEntry redEntry() {
        return new McpCatalogEntry(
                "red-mcp",
                "RED",
                "desc",
                "http://127.0.0.1:3344/mcp",
                "red",
                "CREDENTIALS",
                bearerCredentialAuthConfig(),
                McpConnectMethod.CREDENTIAL_FORM,
                List.of(),
                null,
                new McpConnectProbeConfig("red_ping", "{}", "Invalid RED token"),
                new McpToolSelectionConfig(List.of(), true)
        );
    }
}
