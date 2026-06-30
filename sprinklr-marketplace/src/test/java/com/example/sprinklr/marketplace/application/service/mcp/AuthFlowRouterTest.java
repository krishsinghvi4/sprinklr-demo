package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuthFlowRouterTest {

    private final AuthFlowRouter router = new AuthFlowRouter(
            new OAuthAuthFlowHandler(null, null, null, null, null, null, null),
            new CredentialAuthFlowHandler(null, null)
    );

    @Test
    void routesOAuthEntryToOAuthHandler() {
        var jira = McpCatalogTestFixtures.jiraEntry();
        AuthFlowHandler handler = router.requireHandler(jira);
        assertSame(OAuthAuthFlowHandler.class, handler.getClass());
    }

    @Test
    void routesCredentialEntryToCredentialHandler() {
        var gitlab = McpCatalogTestFixtures.gitlabEntry();
        AuthFlowHandler handler = router.requireHandler(gitlab);
        assertSame(CredentialAuthFlowHandler.class, handler.getClass());
    }

    @Test
    void oauthEntryUsesOAuthRedirectConnectMethod() {
        var jira = McpCatalogTestFixtures.jiraEntry();
        assertEquals(McpConnectMethod.OAUTH_REDIRECT, jira.connectMethod());
    }
}
