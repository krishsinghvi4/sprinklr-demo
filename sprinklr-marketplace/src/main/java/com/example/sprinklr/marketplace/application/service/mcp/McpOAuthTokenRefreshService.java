package com.example.sprinklr.marketplace.application.service.mcp;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthToken;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;

/**
 * Refreshes OAuth access tokens at runtime and persists updated credentials.
 */
@Service
public class McpOAuthTokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(McpOAuthTokenRefreshService.class);

    private final McpCatalogLoader catalogLoader;
    private final McpOAuthClient oauthClient;
    private final CredentialVaultPort credentialVault;
    private final McpConnectionRepository connectionRepository;

    public McpOAuthTokenRefreshService(
            McpCatalogLoader catalogLoader,
            McpOAuthClient oauthClient,
            CredentialVaultPort credentialVault,
            McpConnectionRepository connectionRepository
    ) {
        this.catalogLoader = catalogLoader;
        this.oauthClient = oauthClient;
        this.credentialVault = credentialVault;
        this.connectionRepository = connectionRepository;
    }

    public boolean isOAuthEntry(McpCatalogEntry entry) {
        return entry.authConfig().isOAuth();
    }

    public Map<String, String> refreshIfNeeded(
            McpConnectionDocument connection,
            McpCatalogEntry entry,
            Map<String, String> credentials
    ) {
        if (!isOAuthEntry(entry)) {
            return credentials;
        }
        McpOAuthToken token = McpOAuthToken.fromCredentialMap(credentials);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new McpInvocationException(
                    "OAuth token missing. Please reconnect.",
                    "Missing access token for connectionId=" + connection.id()
            );
        }
        if (!token.isExpired(Instant.now())) {
            return credentials;
        }
        return forceRefresh(connection, entry, credentials, false);
    }

    public Map<String, String> forceRefresh(
            McpConnectionDocument connection,
            McpCatalogEntry entry,
            Map<String, String> credentials
    ) {
        return forceRefresh(connection, entry, credentials, true);
    }

    private Map<String, String> forceRefresh(
            McpConnectionDocument connection,
            McpCatalogEntry entry,
            Map<String, String> credentials,
            boolean clearSession
    ) {
        McpOAuthToken token = McpOAuthToken.fromCredentialMap(credentials);
        if (token == null || token.refreshToken() == null || token.refreshToken().isBlank()) {
            throw new McpInvocationException(
                    "OAuth refresh token missing. Please reconnect.",
                    "Missing refresh token for connectionId=" + connection.id()
            );
        }

        McpOAuthToken refreshed = oauthClient.refreshAccessToken(entry, token.refreshToken());
        String refreshToken = refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()
                ? refreshed.refreshToken()
                : token.refreshToken();
        McpOAuthToken updated = new McpOAuthToken(
                refreshed.accessToken(),
                refreshToken,
                refreshed.expiresAtEpochSec(),
                refreshed.scope(),
                refreshed.tokenType()
        );
        Map<String, String> updatedCredentials = updated.toCredentialMap();
        persistCredentials(connection, updatedCredentials, clearSession);
        log.info("[MCP] OAuth token {} connectionId={}",
                clearSession ? "force-refreshed" : "refreshed", connection.id());
        return updatedCredentials;
    }

    private void persistCredentials(
            McpConnectionDocument connection,
            Map<String, String> credentials,
            boolean clearSession
    ) {
        String encrypted = credentialVault.encrypt(credentials);
        McpConnectionDocument updatedDocument = new McpConnectionDocument(
                connection.id(),
                connection.userId(),
                connection.catalogServerId(),
                connection.serverIdPrefix(),
                encrypted,
                clearSession ? null : connection.mcpSessionId(),
                clearSession ? null : connection.mcpProtocolVersion(),
                connection.status(),
                connection.tools(),
                connection.connectedAt(),
                connection.lastError(),
                connection.toolDependencyGraph(),
                connection.dependencyGraphStatus(),
                connection.redQueryPreferences()
        );
        connectionRepository.save(updatedDocument);
    }
}
