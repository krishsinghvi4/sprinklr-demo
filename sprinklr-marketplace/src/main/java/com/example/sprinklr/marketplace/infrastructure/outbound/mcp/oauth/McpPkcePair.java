package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public record McpPkcePair(String codeVerifier, String codeChallenge) {

    public static McpPkcePair generate() {
        byte[] verifierBytes = new byte[32];
        new SecureRandom().nextBytes(verifierBytes);
        String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return new McpPkcePair(codeVerifier, codeChallenge);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate PKCE challenge", exception);
        }
    }
}
