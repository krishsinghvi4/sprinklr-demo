package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogAuthHeaderBuilderTest {

    private final CatalogAuthHeaderBuilder builder = new CatalogAuthHeaderBuilder();

    @Test
    void buildsOAuthBearerHeader() {
        Map<String, String> headers = builder.buildHeaders(
                McpCatalogTestFixtures.jiraEntry(),
                Map.of("accessToken", "oauth-token")
        );

        assertEquals("Bearer oauth-token", headers.get("Authorization"));
    }

    @Test
    void buildsPrivateTokenHeaderForGitLab() {
        Map<String, String> headers = builder.buildHeaders(
                McpCatalogTestFixtures.gitlabEntry(),
                Map.of("apiToken", "gitlab-pat")
        );

        assertEquals("gitlab-pat", headers.get("Private-Token"));
    }

    @Test
    void buildsBearerHeaderForRed() {
        Map<String, String> headers = builder.buildHeaders(
                McpCatalogTestFixtures.redEntry(),
                Map.of("apiToken", "red-token")
        );

        assertEquals("Bearer red-token", headers.get("Authorization"));
    }

    @Test
    void rejectsMissingOAuthAccessToken() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildHeaders(
                McpCatalogTestFixtures.jiraEntry(),
                Map.of()
        ));
    }

    @Test
    void rejectsMissingCredentialToken() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildHeaders(
                McpCatalogTestFixtures.redEntry(),
                Map.of()
        ));
    }
}
