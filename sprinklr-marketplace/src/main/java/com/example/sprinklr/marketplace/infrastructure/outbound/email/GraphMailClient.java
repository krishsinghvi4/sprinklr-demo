package com.example.sprinklr.marketplace.infrastructure.outbound.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class GraphMailClient {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String fromEmail;
    private final RestClient tokenClient;
    private final RestClient graphClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public GraphMailClient(
            @Value("${app.mail.graph.tenant-id:}") String tenantId,
            @Value("${app.mail.graph.client-id:}") String clientId,
            @Value("${app.mail.graph.client-secret:}") String clientSecret,
            @Value("${spring.mail.username:}") String fromEmail
    ) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.fromEmail = fromEmail;
        this.tokenClient = RestClient.builder()
                .baseUrl("https://login.microsoftonline.com")
                .build();
        this.graphClient = RestClient.builder()
                .baseUrl("https://graph.microsoft.com/v1.0")
                .build();
    }

    public boolean isConfigured() {
        return isPresent(tenantId)
                && isPresent(clientId)
                && isPresent(clientSecret)
                && isPresent(fromEmail);
    }

    public void sendEmail(String toEmail, String subject, String body) {
        if (!isConfigured()) {
            throw new IllegalStateException("Microsoft Graph mail is not configured");
        }

        String token = getAccessToken();
        Map<String, Object> payload = Map.of(
                "message", Map.of(
                        "subject", subject,
                        "body", Map.of("contentType", "Text", "content", body),
                        "toRecipients", List.of(
                                Map.of("emailAddress", Map.of("address", toEmail))
                        )
                ),
                "saveToSentItems", false
        );

        graphClient.post()
                .uri("/users/{fromEmail}/sendMail", fromEmail.trim())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");

        TokenResponse response = tokenClient.post()
                .uri("/{tenantId}/oauth2/v2.0/token", tenantId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken == null || response.accessToken.isBlank()) {
            throw new IllegalStateException("Failed to acquire Microsoft Graph access token");
        }

        cachedToken = response.accessToken;
        long expiresIn = response.expiresIn == null ? 3600L : response.expiresIn;
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {}
}
