package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpOAuthException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.AtlassianOAuthToken;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpPkcePair;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpOAuthStateDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpOAuthStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class McpOAuthService {

    private static final Logger log = LoggerFactory.getLogger(McpOAuthService.class);

    private final McpOAuthStateRepository stateRepository;
    private final McpCatalogLoader catalogLoader;
    private final McpOAuthClient oauthClient;
    private final McpMarketplaceService marketplaceService;
    private final McpProperties properties;

    public McpOAuthService(
            McpOAuthStateRepository stateRepository,
            McpCatalogLoader catalogLoader,
            McpOAuthClient oauthClient,
            McpMarketplaceService marketplaceService,
            McpProperties properties
    ) {
        this.stateRepository = stateRepository;
        this.catalogLoader = catalogLoader;
        this.oauthClient = oauthClient;
        this.marketplaceService = marketplaceService;
        this.properties = properties;
    }

    public String startOAuth(String userId, String catalogServerId) {
        McpCatalogEntry entry = catalogLoader.findById(catalogServerId)
                .orElseThrow(() -> new McpOAuthException(
                        "Unknown MCP server",
                        "OAuth start failed: catalog server not found " + catalogServerId));

        if (!"OAUTH_ATLASSIAN".equals(entry.authType())) {
            throw new McpOAuthException(
                    "OAuth is not enabled for " + entry.displayName(),
                    "OAuth start attempted for authType=" + entry.authType());
        }

        validateOAuthConfig();

        McpPkcePair pkce = McpPkcePair.generate();
        String state = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getOauthStateTtlSeconds());
        stateRepository.save(new McpOAuthStateDocument(
                state, userId, catalogServerId, pkce.codeVerifier(), now, expiresAt));

        String authUrl = oauthClient.buildAuthorizationUrl(state, pkce.codeChallenge());
        log.info("[MCP] OAuth start userId={} catalogServerId={} redirectUri={} flow=mcp-dcr",
                userId, catalogServerId, properties.getOauthRedirectUri());
        return authUrl;
    }

    public void handleCallback(String state, String code) {
        McpOAuthStateDocument stateDocument = stateRepository.findById(state)
                .orElseThrow(() -> new McpOAuthException(
                        "OAuth session expired. Please try again.",
                        "OAuth state not found for state=" + state));
        stateRepository.deleteById(state);

        AtlassianOAuthToken token = oauthClient.exchangeCodeForTokens(code, stateDocument.codeVerifier());

        if (token.refreshToken() == null || token.refreshToken().isBlank()) {
            throw new McpOAuthException(
                    "OAuth refresh token missing. Please reconnect.",
                    "OAuth exchange missing refresh_token for userId=" + stateDocument.userId());
        }

        Map<String, String> credentials = token.toCredentialMap();
        marketplaceService.connectOAuth(stateDocument.userId(), stateDocument.catalogServerId(), credentials);
        log.info("[MCP] OAuth callback completed userId={} catalogServerId={}",
                stateDocument.userId(), stateDocument.catalogServerId());
    }

    private void validateOAuthConfig() {
        if (properties.getOauthRedirectUri() == null || properties.getOauthRedirectUri().isBlank()) {
            throw new McpOAuthException(
                    "OAuth configuration missing",
                    "Missing OAuth redirect URI");
        }
    }
}
