package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpDcrClientDocument;
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
import java.util.Map;

@Component
public class McpOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(McpOAuthClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long EXPIRY_SKEW_SECONDS = 60;

    private final WebClient webClient;
    private final McpProperties properties;
    private final McpDcrRegistrationService dcrRegistrationService;

    public McpOAuthClient(
            @Qualifier("mcpWebClient") WebClient webClient,
            McpProperties properties,
            McpDcrRegistrationService dcrRegistrationService
    ) {
        this.webClient = webClient;
        this.properties = properties;
        this.dcrRegistrationService = dcrRegistrationService;
    }

    public String buildAuthorizationUrl(String state, String codeChallenge) {
        McpDcrClientDocument dcrClient = dcrRegistrationService.getOrRegisterClient();
        McpOAuthMetadata metadata = dcrRegistrationService.metadata();

        return UriComponentsBuilder.fromUriString(metadata.authorizationEndpoint())
                .queryParam("client_id", dcrClient.clientId())
                .queryParam("redirect_uri", properties.getOauthRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .queryParam("scope", properties.getOauthScopes())
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("resource", properties.getOauthResource())
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    public AtlassianOAuthToken exchangeCodeForTokens(String code, String codeVerifier) {
        McpDcrClientDocument dcrClient = dcrRegistrationService.getOrRegisterClient();
        McpOAuthMetadata metadata = dcrRegistrationService.metadata();

        return requestTokens(
                metadata.tokenEndpoint(),
                Map.of(
                        "grant_type", "authorization_code",
                        "client_id", dcrClient.clientId(),
                        "client_secret", dcrClient.clientSecret(),
                        "code", code,
                        "redirect_uri", properties.getOauthRedirectUri(),
                        "code_verifier", codeVerifier,
                        "resource", properties.getOauthResource()
                )
        );
    }

    public AtlassianOAuthToken refreshAccessToken(String refreshToken) {
        McpDcrClientDocument dcrClient = dcrRegistrationService.getOrRegisterClient();
        McpOAuthMetadata metadata = dcrRegistrationService.metadata();

        return requestTokens(
                metadata.tokenEndpoint(),
                Map.of(
                        "grant_type", "refresh_token",
                        "client_id", dcrClient.clientId(),
                        "client_secret", dcrClient.clientSecret(),
                        "refresh_token", refreshToken,
                        "resource", properties.getOauthResource()
                )
        );
    }

    private AtlassianOAuthToken requestTokens(String tokenEndpoint, Map<String, String> payload) {
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
            log.warn("[MCP] OAuth token request failed status={} redirectUri={} body={}",
                    exception.getStatusCode().value(),
                    properties.getOauthRedirectUri(),
                    responseBody);
            throw new McpConnectionException(
                    tokenExchangeUserMessage(exception.getStatusCode().value()),
                    "OAuth token request error: status="
                            + exception.getStatusCode().value()
                            + " body="
                            + responseBody);
        } catch (Exception exception) {
            log.warn("[MCP] OAuth token request failed redirectUri={}: {}",
                    properties.getOauthRedirectUri(), exception.getMessage());
            throw new McpConnectionException(
                    "OAuth token exchange failed",
                    "OAuth token request error: " + exception.getMessage());
        }
    }

    private AtlassianOAuthToken parseTokenResponse(String responseBody) throws Exception {
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
        return new AtlassianOAuthToken(accessToken, refreshToken, expiresAt, scope, tokenType);
    }

    private String tokenExchangeUserMessage(int statusCode) {
        if (statusCode == 401) {
            return "OAuth token exchange failed. Please disconnect and reconnect Jira from your Profile page.";
        }
        return "OAuth token exchange failed";
    }
}
