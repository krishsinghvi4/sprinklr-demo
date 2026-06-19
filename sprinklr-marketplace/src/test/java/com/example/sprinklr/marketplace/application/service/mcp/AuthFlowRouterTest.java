package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.AtlassianOAuthAuthStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuthFlowRouterTest {

    private final AuthFlowRouter router = new AuthFlowRouter(
            new OAuthAuthFlowHandler(null, null, null, null, null, null, null),
            new CredentialAuthFlowHandler(null, null)
    );

    @Test
    void routesOAuthEntryToOAuthHandler() {
        McpCatalogEntry jira = McpCatalogTestFixtures.jiraEntry();
        AuthFlowHandler handler = router.requireHandler(jira);
        assertSame(OAuthAuthFlowHandler.class, handler.getClass());
    }

    @Test
    void routesCredentialEntryToCredentialHandler() {
        McpCatalogEntry gitlab = new McpCatalogEntry(
                "gitlab-mcp",
                "GitLab",
                "desc",
                "https://gitlab.example.com/mcp",
                "gitlab",
                "GITLAB_PRIVATE_TOKEN",
                new McpAuthConfig(McpAuthKind.CREDENTIALS, null),
                McpConnectMethod.CREDENTIAL_FORM,
                List.of(),
                null
        );
        AuthFlowHandler handler = router.requireHandler(gitlab);
        assertSame(CredentialAuthFlowHandler.class, handler.getClass());
    }

    @Test
    void infersConnectMethodFromLegacyOAuthAuthType() {
        McpCatalogEntry legacy = new McpCatalogEntry(
                "atlassian-jira",
                "Jira",
                "desc",
                "https://mcp.atlassian.com/v1/mcp/authv2",
                "jira",
                AtlassianOAuthAuthStrategy.AUTH_TYPE,
                new McpAuthConfig(McpAuthKind.OAUTH, new McpOAuthCatalogConfig(
                        "atlassian",
                        "https://mcp.atlassian.com/.well-known/oauth-authorization-server",
                        "https://mcp.atlassian.com/v1/mcp/authv2",
                        "offline_access",
                        true,
                        true,
                        true,
                        true
                )),
                McpConnectMethod.OAUTH_REDIRECT,
                List.of(),
                null
        );
        assertEquals(McpConnectMethod.OAUTH_REDIRECT, legacy.connectMethod());
    }
}
