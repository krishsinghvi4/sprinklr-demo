package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class BasicEmailTokenAuthStrategy implements McpAuthStrategy {

    public static final String AUTH_TYPE = "BASIC_EMAIL_TOKEN";

    @Override
    public String authType() {
        return AUTH_TYPE;
    }

    @Override
    public Map<String, String> buildAuthHeaders(Map<String, String> credentials) {
        String email = credentials.get("email");
        String apiToken = credentials.get("apiToken");
        if (email == null || email.isBlank() || apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("email and apiToken are required for BASIC_EMAIL_TOKEN auth");
        }
        String encoded = Base64.getEncoder()
                .encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        return Map.of("Authorization", "Basic " + encoded);
    }
}
