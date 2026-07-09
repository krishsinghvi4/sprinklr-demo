package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke;

import com.example.sprinklr.marketplace.application.service.mcp.McpOAuthTokenRefreshService;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.CatalogAuthHeaderBuilder;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.MergedCatalogResolver;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.McpConnectionRepository;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Prepares auth headers, MCP session, and normalized tool arguments before tools/call.
 */
@Component
public class McpInvocationPreparer {

    private static final Logger log = LoggerFactory.getLogger(McpInvocationPreparer.class);
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";

    private final McpConnectionRepository connectionRepository;
    private final CredentialVaultPort credentialVault;
    private final MergedCatalogResolver catalogResolver;
    private final CatalogAuthHeaderBuilder authHeaderBuilder;
    private final StreamableHttpMcpClient mcpClient;
    private final McpOAuthTokenRefreshService oauthTokenRefreshService;
    private final CompositeMcpToolArgumentNormalizer argumentNormalizer;

    public McpInvocationPreparer(
            McpConnectionRepository connectionRepository,
            CredentialVaultPort credentialVault,
            MergedCatalogResolver catalogResolver,
            CatalogAuthHeaderBuilder authHeaderBuilder,
            StreamableHttpMcpClient mcpClient,
            McpOAuthTokenRefreshService oauthTokenRefreshService,
            CompositeMcpToolArgumentNormalizer argumentNormalizer
    ) {
        this.connectionRepository = connectionRepository;
        this.credentialVault = credentialVault;
        this.catalogResolver = catalogResolver;
        this.authHeaderBuilder = authHeaderBuilder;
        this.mcpClient = mcpClient;
        this.oauthTokenRefreshService = oauthTokenRefreshService;
        this.argumentNormalizer = argumentNormalizer;
    }

    public PreparedInvocation prepare(
            McpConnectionDocument connection,
            String toolName,
            String argumentsJson
    ) {
        McpCatalogEntry catalogEntry = catalogResolver.findById(connection.catalogServerId(), connection.userId())
                .orElseThrow(() -> new McpInvocationException(
                        "MCP server configuration not found",
                        "Catalog entry missing for " + connection.catalogServerId()
                ));

        Map<String, String> credentials = credentialVault.decrypt(connection.encryptedCredentials());
        if (catalogEntry.authConfig().isOAuth()) {
            credentials = oauthTokenRefreshService.refreshIfNeeded(connection, catalogEntry, credentials);
        }
        Map<String, String> authHeaders = authHeaderBuilder.buildHeaders(catalogEntry, credentials);
        StreamableHttpMcpClient.McpSession session = resolveSession(connection, catalogEntry, authHeaders);
        String normalizedArguments = argumentNormalizer.normalize(
                catalogEntry,
                toolName,
                argumentsJson,
                connection.id()
        );

        return new PreparedInvocation(catalogEntry, credentials, authHeaders, session, normalizedArguments);
    }

    public PreparedInvocation refreshOAuthAndReprepare(
            McpConnectionDocument connection,
            String toolName,
            String argumentsJson,
            Map<String, String> credentials
    ) {
        McpCatalogEntry catalogEntry = catalogResolver.findById(connection.catalogServerId(), connection.userId())
                .orElseThrow(() -> new McpInvocationException(
                        "MCP server configuration not found",
                        "Catalog entry missing for " + connection.catalogServerId()
                ));
        Map<String, String> refreshedCredentials =
                oauthTokenRefreshService.forceRefresh(connection, catalogEntry, credentials);
        Map<String, String> authHeaders = authHeaderBuilder.buildHeaders(catalogEntry, refreshedCredentials);
        StreamableHttpMcpClient.McpSession session = reinitializeSession(connection, catalogEntry, authHeaders);
        String normalizedArguments = argumentNormalizer.normalize(
                catalogEntry,
                toolName,
                argumentsJson,
                connection.id()
        );
        return new PreparedInvocation(catalogEntry, refreshedCredentials, authHeaders, session, normalizedArguments);
    }

    public StreamableHttpMcpClient.McpSession reinitializeSession(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders
    ) {
        log.info("[MCP] Re-initializing session connectionId={}", connection.id());
        StreamableHttpMcpClient.McpSession session = mcpClient.initialize(catalogEntry.endpointUrl(), authHeaders);
        connectionRepository.save(copyWithSession(connection, session));
        return session;
    }

    private StreamableHttpMcpClient.McpSession resolveSession(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders
    ) {
        String sessionId = connection.mcpSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.info("[MCP] No stored session, initializing connectionId={} catalog={}",
                    connection.id(), connection.catalogServerId());
            StreamableHttpMcpClient.McpSession session = mcpClient.initialize(catalogEntry.endpointUrl(), authHeaders);
            connectionRepository.save(copyWithSession(connection, session));
            return session;
        }
        String protocolVersion = connection.mcpProtocolVersion() != null
                ? connection.mcpProtocolVersion()
                : DEFAULT_PROTOCOL_VERSION;
        return new StreamableHttpMcpClient.McpSession(sessionId, protocolVersion);
    }

    private static McpConnectionDocument copyWithSession(
            McpConnectionDocument connection,
            StreamableHttpMcpClient.McpSession session
    ) {
        return new McpConnectionDocument(
                connection.id(),
                connection.userId(),
                connection.catalogServerId(),
                connection.serverIdPrefix(),
                connection.encryptedCredentials(),
                session.sessionId(),
                session.protocolVersion(),
                connection.status(),
                connection.tools(),
                connection.connectedAt(),
                connection.lastError(),
                connection.toolDependencyGraph(),
                connection.dependencyGraphStatus(),
                connection.redQueryPreferences()
        );
    }

    public record PreparedInvocation(
            McpCatalogEntry catalogEntry,
            Map<String, String> credentials,
            Map<String, String> authHeaders,
            StreamableHttpMcpClient.McpSession session,
            String argumentsJson
    ) {
    }
}
