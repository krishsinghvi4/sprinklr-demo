package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpOAuthException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthConfigResolver;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthToken;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpPkcePair;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.McpOAuthStateDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.McpOAuthStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth redirect connect flow: PKCE state persistence, authorization URL, callback token exchange.
 */
@Component
public class OAuthAuthFlowHandler implements AuthFlowHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthAuthFlowHandler.class);

    private final McpOAuthStateRepository stateRepository;
    private final McpCatalogLoader catalogLoader;
    private final McpOAuthClient oauthClient;
    private final McpOAuthConfigResolver oauthConfigResolver;
    private final McpProviderResolver providerResolver;
    private final McpConnectionOrchestrator connectionOrchestrator;
    private final McpProperties properties;

    public OAuthAuthFlowHandler(
            McpOAuthStateRepository stateRepository,
            McpCatalogLoader catalogLoader,
            McpOAuthClient oauthClient,
            McpOAuthConfigResolver oauthConfigResolver,
            McpProviderResolver providerResolver,
            McpConnectionOrchestrator connectionOrchestrator,
            McpProperties properties
    ) {
        this.stateRepository = stateRepository;
        this.catalogLoader = catalogLoader;
        this.oauthClient = oauthClient;
        this.oauthConfigResolver = oauthConfigResolver;
        this.providerResolver = providerResolver;
        this.connectionOrchestrator = connectionOrchestrator;
        this.properties = properties;
    }

    @Override
    public McpConnectMethod connectMethod() {
        return McpConnectMethod.OAUTH_REDIRECT;
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return entry.connectMethod() == McpConnectMethod.OAUTH_REDIRECT && entry.authConfig().isOAuth();
    }

    public String startOAuth(String userId, String catalogServerId) {
        McpCatalogEntry entry = catalogLoader.findById(catalogServerId)
                .orElseThrow(() -> new McpOAuthException(
                        "Unknown MCP server",
                        "OAuth start failed: catalog server not found " + catalogServerId));

        if (!supports(entry)) {
            throw new McpOAuthException(
                    "OAuth is not enabled for " + entry.displayName(),
                    "OAuth start attempted for connectMethod=" + entry.connectMethod());
        }

        validateOAuthConfig();
        McpProvider provider = providerResolver.resolve(entry);
        String providerKey = provider.providerKey(entry);

        // Remove stale in-flight states so mcp_oauth_states does not accumulate.
        stateRepository.deleteByUserIdAndCatalogServerId(userId, catalogServerId);

        McpPkcePair pkce = McpPkcePair.generate();
        String state = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getOauthStateTtlSeconds());

        stateRepository.save(new McpOAuthStateDocument(
                state,
                userId,
                catalogServerId,
                providerKey,
                pkce.codeVerifier(),
                now,
                expiresAt
        ));

        String authUrl = oauthClient.buildAuthorizationUrl(entry, state, pkce.codeChallenge());
        log.info("[MCP] OAuth start userId={} catalogServerId={} providerKey={} redirectUri={}",
                userId, catalogServerId, providerKey, oauthConfigResolver.redirectUri());
        return authUrl;
    }

    public void handleCallback(String state, String code) {
        McpOAuthStateDocument stateDocument = stateRepository.findById(state)
                .orElseThrow(() -> new McpOAuthException(
                        "OAuth session expired. Please try again.",
                        "OAuth state not found for state=" + state));

        if (stateDocument.expiresAt().isBefore(Instant.now())) {
            deleteStateSafely(state);
            throw new McpOAuthException(
                    "OAuth session expired. Please try again.",
                    "OAuth state expired for state=" + state);
        }

        // Always delete state on use — one-time CSRF token regardless of outcome below.
        deleteStateSafely(state);

        McpCatalogEntry entry = catalogLoader.findById(stateDocument.catalogServerId())
                .orElseThrow(() -> new McpOAuthException(
                        "Unknown MCP server",
                        "OAuth callback catalog entry missing " + stateDocument.catalogServerId()));

        McpOAuthToken token;
        try {
            token = oauthClient.exchangeCodeForTokens(entry, code, stateDocument.codeVerifier());
        } catch (RuntimeException exception) {
            log.warn("[MCP] OAuth token exchange failed userId={} catalogServerId={}: {}",
                    stateDocument.userId(), stateDocument.catalogServerId(), exception.getMessage());
            throw exception;
        }

        if (entry.authConfig().oauth().requiresRefreshToken()
                && (token.refreshToken() == null || token.refreshToken().isBlank())) {
            throw new McpOAuthException(
                    "OAuth refresh token missing. Please reconnect.",
                    "OAuth exchange missing refresh_token for userId=" + stateDocument.userId());
        }

        Map<String, String> credentials = token.toCredentialMap();
        connectionOrchestrator.connectWithCredentials(
                stateDocument.userId(),
                stateDocument.catalogServerId(),
                credentials
        );
        log.info("[MCP] OAuth callback completed userId={} catalogServerId={}",
                stateDocument.userId(), stateDocument.catalogServerId());
    }

    /** Deletes OAuth state; logs but does not fail if already removed. */
    public void deleteStateSafely(String stateId) {
        try {
            stateRepository.deleteById(stateId);
        } catch (Exception exception) {
            log.warn("[MCP] Failed to delete OAuth state id={}: {}", stateId, exception.getMessage());
        }
    }

    private void validateOAuthConfig() {
        if (oauthConfigResolver.redirectUri() == null || oauthConfigResolver.redirectUri().isBlank()) {
            throw new McpOAuthException(
                    "OAuth configuration missing",
                    "Missing OAuth redirect URI");
        }
    }
}
