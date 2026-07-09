package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import org.springframework.stereotype.Component;

/**
 * Routes connect requests to the correct auth flow handler based on catalog connectMethod.
 */
@Component
public class AuthFlowRouter {

    private final OAuthAuthFlowHandler oauthHandler;
    private final CredentialAuthFlowHandler credentialHandler;

    public AuthFlowRouter(OAuthAuthFlowHandler oauthHandler, CredentialAuthFlowHandler credentialHandler) {
        this.oauthHandler = oauthHandler;
        this.credentialHandler = credentialHandler;
    }

    public OAuthAuthFlowHandler oauth() {
        return oauthHandler;
    }

    public CredentialAuthFlowHandler credentials() {
        return credentialHandler;
    }

    /** Returns the handler responsible for the entry's connect method. */
    public AuthFlowHandler requireHandler(McpCatalogEntry entry) {
        if (entry.connectMethod() == McpConnectMethod.OAUTH_REDIRECT) {
            if (!oauthHandler.supports(entry)) {
                throw new IllegalArgumentException(
                        "OAuth connect is not configured for " + entry.displayName());
            }
            return oauthHandler;
        }
        if (!credentialHandler.supports(entry)) {
            throw new IllegalArgumentException(
                    "Credential connect is not configured for " + entry.displayName());
        }
        return credentialHandler;
    }

    public void assertCredentialConnectAllowed(McpCatalogEntry entry) {
        if (entry.connectMethod() == McpConnectMethod.OAUTH_REDIRECT) {
            throw new IllegalArgumentException(
                    "OAuth connection required for " + entry.displayName()
                            + ". Use the OAuth connect flow instead.");
        }
    }

    public void assertOAuthConnectAllowed(McpCatalogEntry entry) {
        if (entry.connectMethod() != McpConnectMethod.OAUTH_REDIRECT) {
            throw new IllegalArgumentException(
                    "OAuth is not enabled for " + entry.displayName());
        }
    }
}
