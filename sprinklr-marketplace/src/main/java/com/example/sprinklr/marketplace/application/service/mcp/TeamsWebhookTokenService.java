package com.example.sprinklr.marketplace.application.service.mcp;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class TeamsWebhookTokenService {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookTokenService.class);

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String EXPECTED_PURPOSE = "teams-webhook";
    private static final long ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000;

    private final SecretKey signingKey;
    private final String ingestBaseUrl;

    public TeamsWebhookTokenService(
            @Value("${app.teams.webhook.signing-secret}") String signingSecret,
            @Value("${app.teams.ingest-base-url}") String ingestBaseUrl
    ) {
        this.signingKey = Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
        this.ingestBaseUrl = trimTrailingSlash(ingestBaseUrl);
        String prefix = signingSecret.length() >= 8 ? signingSecret.substring(0, 8) : signingSecret;
        log.info("[TeamsWebhook] Signing key prefix={}... ingestBaseUrl={}", prefix, this.ingestBaseUrl);
    }

    public String generateToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(userId)
                .claim(PURPOSE_CLAIM, EXPECTED_PURPOSE)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ONE_YEAR_MS))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String buildWebhookUrl(String token) {
        return ingestBaseUrl + "/webhook/teams?token=" + token;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
