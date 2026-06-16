package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
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

@Service
public class McpDcrRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(McpDcrRegistrationService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final McpProperties properties;
    private final McpDcrClientRepository dcrClientRepository;
    private volatile McpOAuthMetadata cachedMetadata;

    public McpDcrRegistrationService(
            @Qualifier("mcpWebClient") WebClient webClient,
            McpProperties properties,
            McpDcrClientRepository dcrClientRepository
    ) {
        this.webClient = webClient;
        this.properties = properties;
        this.dcrClientRepository = dcrClientRepository;
    }

    public McpOAuthMetadata metadata() {
        McpOAuthMetadata local = cachedMetadata;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedMetadata != null) {
                return cachedMetadata;
            }
            cachedMetadata = fetchMetadata();
            return cachedMetadata;
        }
    }

    public McpDcrClientDocument getOrRegisterClient() {
        String redirectUri = properties.getOauthRedirectUri();
        return dcrClientRepository.findByRedirectUri(redirectUri)
                .orElseGet(() -> registerClient(redirectUri));
    }

    private McpDcrClientDocument registerClient(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new McpConnectionException(
                    "OAuth redirect URI is not configured",
                    "Missing app.mcp.oauth-redirect-uri");
        }

        McpOAuthMetadata metadata = metadata();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_name", "sprinklr-marketplace");
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
                    redirectUri,
                    clientId,
                    clientSecret,
                    Instant.now()
            );
            McpDcrClientDocument saved = dcrClientRepository.save(document);
            log.info("[MCP] DCR client registered redirectUri={} clientId={}", redirectUri, clientId);
            return saved;
        } catch (McpConnectionException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            log.warn("[MCP] DCR registration failed status={} body={}",
                    exception.getStatusCode().value(), exception.getResponseBodyAsString());
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

    private McpOAuthMetadata fetchMetadata() {
        try {
            String responseBody = webClient.get()
                    .uri(properties.getOauthMetadataUrl())
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
                        "Missing endpoints in OAuth metadata");
            }
            return new McpOAuthMetadata(authorizationEndpoint, tokenEndpoint, registrationEndpoint);
        } catch (McpConnectionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new McpConnectionException(
                    "OAuth metadata unavailable",
                    "Failed to load OAuth metadata: " + exception.getMessage());
        }
    }
}
