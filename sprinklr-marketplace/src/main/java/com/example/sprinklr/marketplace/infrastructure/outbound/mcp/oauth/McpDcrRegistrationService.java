package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpDcrClientDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpDcrClientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Client Registration (DCR) and OAuth metadata discovery, scoped per provider key.
 */
@Service
public class McpDcrRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(McpDcrRegistrationService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LEGACY_PROVIDER_KEY = "legacy";

    private final WebClient webClient;
    private final McpDcrClientRepository dcrClientRepository;
    private final McpOAuthConfigResolver oauthConfigResolver;
    private final ConcurrentHashMap<String, McpOAuthMetadata> metadataCache = new ConcurrentHashMap<>();

    public McpDcrRegistrationService(
            @Qualifier("mcpWebClient") WebClient webClient,
            McpDcrClientRepository dcrClientRepository,
            McpOAuthConfigResolver oauthConfigResolver
    ) {
        this.webClient = webClient;
        this.dcrClientRepository = dcrClientRepository;
        this.oauthConfigResolver = oauthConfigResolver;
    }

    public McpOAuthMetadata metadata(McpCatalogEntry entry) {
        McpOAuthCatalogConfig oauth = oauthConfigResolver.resolve(entry);
        return metadataCache.computeIfAbsent(oauth.providerKey(), key -> fetchMetadata(oauth.metadataUrl()));
    }

    public McpDcrClientDocument getOrRegisterClient(McpCatalogEntry entry) {
        McpOAuthCatalogConfig oauth = oauthConfigResolver.resolve(entry);
        String redirectUri = oauthConfigResolver.redirectUri();
        String providerKey = oauth.providerKey();

        return dcrClientRepository.findByProviderKeyAndRedirectUri(providerKey, redirectUri)
                .or(() -> migrateLegacyClient(providerKey, redirectUri))
                .orElseGet(() -> registerClient(entry, oauth, redirectUri, providerKey));
    }

    private java.util.Optional<McpDcrClientDocument> migrateLegacyClient(String providerKey, String redirectUri) {
        if (LEGACY_PROVIDER_KEY.equals(providerKey)) {
            return java.util.Optional.empty();
        }
        return dcrClientRepository.findByRedirectUri(redirectUri)
                .flatMap(legacy -> {
                    String storedKey = legacy.providerKey();
                    if (providerKey.equals(storedKey)) {
                        // Already keyed for this provider; primary lookup missed (e.g. index lag).
                        return java.util.Optional.of(legacy);
                    }
                    if (storedKey != null && !LEGACY_PROVIDER_KEY.equals(storedKey)) {
                        // Redirect URI is registered for a different OAuth provider.
                        return java.util.Optional.empty();
                    }
                    McpDcrClientDocument migrated = new McpDcrClientDocument(
                            legacy.id(),
                            providerKey,
                            legacy.redirectUri(),
                            legacy.clientId(),
                            legacy.clientSecret(),
                            legacy.registeredAt()
                    );
                    log.info("[MCP] Migrating DCR client to providerKey={} redirectUri={}", providerKey, redirectUri);
                    return java.util.Optional.of(dcrClientRepository.save(migrated));
                });
    }

    private McpDcrClientDocument registerClient(
            McpCatalogEntry entry,
            McpOAuthCatalogConfig oauth,
            String redirectUri,
            String providerKey
    ) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new McpConnectionException(
                    "OAuth redirect URI is not configured",
                    "Missing app.mcp.oauth-redirect-uri");
        }
        if (!oauth.useDcr()) {
            throw new McpConnectionException(
                    "OAuth DCR is not enabled for " + entry.displayName(),
                    "DCR disabled for catalogServerId=" + entry.id());
        }

        McpOAuthMetadata metadata = metadata(entry);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_name", "sprinklr-marketplace-" + providerKey);
        body.put("redirect_uris", java.util.List.of(redirectUri));
        body.put("grant_types", java.util.List.of("authorization_code", "refresh_token"));
        body.put("response_types", java.util.List.of("code"));
        body.put("token_endpoint_auth_method", "client_secret_post");

        try {
            String requestJson = OBJECT_MAPPER.writeValueAsString(body);

            String responseBody = webClient.post()
                    .uri(metadata.registrationEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String clientId = root.path("client_id").asText(null);
            String clientSecret = root.path("client_secret").asText(null);
            if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
                throw new McpConnectionException(
                        "OAuth client registration failed",
                        "DCR response missing client_id/client_secret");
            }

            McpDcrClientDocument document = new McpDcrClientDocument(
                    UUID.randomUUID().toString(),
                    providerKey,
                    redirectUri,
                    clientId,
                    clientSecret,
                    Instant.now()
            );
            McpDcrClientDocument saved = dcrClientRepository.save(document);
            log.info("[MCP] DCR client registered providerKey={} redirectUri={} clientId={}",
                    providerKey, redirectUri, clientId);
            return saved;
        } catch (McpConnectionException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            log.warn("[MCP] DCR registration failed providerKey={} status={} body={}",
                    providerKey, exception.getStatusCode().value(), exception.getResponseBodyAsString());
            throw new McpConnectionException(
                    "OAuth client registration failed",
                    "DCR registration error status="
                            + exception.getStatusCode().value()
                            + " body="
                            + exception.getResponseBodyAsString());
        } catch (Exception exception) {
            throw new McpConnectionException(
                    "OAuth client registration failed",
                    "DCR registration error: " + exception.getMessage());
        }
    }

    private McpOAuthMetadata fetchMetadata(String metadataUrl) {
        try {
            String responseBody = webClient.get()
                    .uri(metadataUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String authorizationEndpoint = root.path("authorization_endpoint").asText(null);
            String tokenEndpoint = root.path("token_endpoint").asText(null);
            String registrationEndpoint = root.path("registration_endpoint").asText(null);
            if (authorizationEndpoint == null || tokenEndpoint == null || registrationEndpoint == null) {
                throw new McpConnectionException(
                        "OAuth metadata unavailable",
                        "Missing endpoints in OAuth metadata url=" + metadataUrl);
            }
            return new McpOAuthMetadata(authorizationEndpoint, tokenEndpoint, registrationEndpoint);
        } catch (McpConnectionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new McpConnectionException(
                    "OAuth metadata unavailable",
                    "Failed to load OAuth metadata url=" + metadataUrl + ": " + exception.getMessage());
        }
    }
}
