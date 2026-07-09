package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.McpDcrClientDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic OAuth client for catalog-driven MCP providers (authorization URL, token exchange, refresh).
 */
@Component
public class McpOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(McpOAuthClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long EXPIRY_SKEW_SECONDS = 60;

    private final WebClient webClient;
    private final McpOAuthConfigResolver oauthConfigResolver;
    private final McpDcrRegistrationService dcrRegistrationService;

    public McpOAuthClient(
            @Qualifier("mcpWebClient") WebClient webClient,
            McpOAuthConfigResolver oauthConfigResolver,
            McpDcrRegistrationService dcrRegistrationService
    ) {
        this.webClient = webClient;
        this.oauthConfigResolver = oauthConfigResolver;
        this.dcrRegistrationService = dcrRegistrationService;
    }

    public String buildAuthorizationUrl(McpCatalogEntry entry, String state, String codeChallenge) {
        McpOAuthCatalogConfig oauth = oauthConfigResolver.resolve(entry);
        McpOAuthMetadata metadata = dcrRegistrationService.metadata(entry);
        McpDcrClientDocument dcrClient = dcrRegistrationService.getOrRegisterClient(entry);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(metadata.authorizationEndpoint())
                .queryParam("client_id", dcrClient.clientId())
                .queryParam("redirect_uri", oauthConfigResolver.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .queryParam("scope", oauth.scopes());

        if (oauth.usePkce()) {
            builder.queryParam("code_challenge", codeChallenge)
                    .queryParam("code_challenge_method", "S256");
        }
        if (oauth.includeResourceParam() && oauth.resource() != null && !oauth.resource().isBlank()) {
            builder.queryParam("resource", oauth.resource());
        }

        return builder.encode(StandardCharsets.UTF_8).build().toUriString();
    }

    public McpOAuthToken exchangeCodeForTokens(McpCatalogEntry entry, String code, String codeVerifier) {
        McpOAuthCatalogConfig oauth = oauthConfigResolver.resolve(entry);
        McpOAuthMetadata metadata = dcrRegistrationService.metadata(entry);
        McpDcrClientDocument dcrClient = dcrRegistrationService.getOrRegisterClient(entry);

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("grant_type", "authorization_code");
        payload.put("client_id", dcrClient.clientId());
        payload.put("client_secret", dcrClient.clientSecret());
        payload.put("code", code);
        payload.put("redirect_uri", oauthConfigResolver.redirectUri());
        if (oauth.usePkce()) {
            payload.put("code_verifier", codeVerifier);
        }
        if (oauth.includeResourceParam() && oauth.resource() != null && !oauth.resource().isBlank()) {
            payload.put("resource", oauth.resource());
        }

        return requestTokens(entry, metadata.tokenEndpoint(), payload);
    }

    public McpOAuthToken refreshAccessToken(McpCatalogEntry entry, String refreshToken) {
        McpOAuthCatalogConfig oauth = oauthConfigResolver.resolve(entry);
        McpOAuthMetadata metadata = dcrRegistrationService.metadata(entry);
        McpDcrClientDocument dcrClient = dcrRegistrationService.getOrRegisterClient(entry);

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("grant_type", "refresh_token");
        payload.put("client_id", dcrClient.clientId());
        payload.put("client_secret", dcrClient.clientSecret());
        payload.put("refresh_token", refreshToken);
        if (oauth.includeResourceParam() && oauth.resource() != null && !oauth.resource().isBlank()) {
            payload.put("resource", oauth.resource());
        }

        return requestTokens(entry, metadata.tokenEndpoint(), payload);
    }

    private McpOAuthToken requestTokens(McpCatalogEntry entry, String tokenEndpoint, Map<String, String> payload) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            payload.forEach(form::add);

            String responseBody = webClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseTokenResponse(responseBody);
        } catch (McpConnectionException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();
            log.warn("[MCP] OAuth token request failed catalogServerId={} status={} body={}",
                    entry.id(), exception.getStatusCode().value(), responseBody);
            throw new McpConnectionException(
                    tokenExchangeUserMessage(entry, exception.getStatusCode().value()),
                    "OAuth token request error: status="
                            + exception.getStatusCode().value()
                            + " body="
                            + responseBody);
        } catch (Exception exception) {
            log.warn("[MCP] OAuth token request failed catalogServerId={}: {}",
                    entry.id(), exception.getMessage());
            throw new McpConnectionException(
                    "OAuth token exchange failed",
                    "OAuth token request error: " + exception.getMessage());
        }
    }

    private McpOAuthToken parseTokenResponse(String responseBody) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        String accessToken = root.path("access_token").asText(null);
        String refreshToken = root.path("refresh_token").asText(null);
        long expiresIn = root.path("expires_in").asLong(0);
        String scope = root.path("scope").asText(null);
        String tokenType = root.path("token_type").asText(null);

        if (accessToken == null || accessToken.isBlank()) {
            String error = root.path("error").asText(null);
            String description = root.path("error_description").asText(null);
            throw new McpConnectionException(
                    "OAuth token exchange failed",
                    "Missing access_token in OAuth response"
                            + (error != null ? " error=" + error : "")
                            + (description != null ? " description=" + description : ""));
        }

        long expiresAt = Instant.now().getEpochSecond() + Math.max(expiresIn - EXPIRY_SKEW_SECONDS, 0);
        return new McpOAuthToken(accessToken, refreshToken, expiresAt, scope, tokenType);
    }

    private String tokenExchangeUserMessage(McpCatalogEntry entry, int statusCode) {
        if (statusCode == 401) {
            return "OAuth token exchange failed. Please disconnect and reconnect "
                    + entry.displayName() + " from your Profile page.";
        }
        return "OAuth token exchange failed";
    }
}
