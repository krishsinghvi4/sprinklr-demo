package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitLabPrivateTokenAuthStrategyTest {

    @Test
    void buildsPrivateTokenHeaderByDefault() {
        McpProperties properties = new McpProperties();
        GitLabPrivateTokenAuthStrategy strategy = new GitLabPrivateTokenAuthStrategy(properties);

        Map<String, String> headers = strategy.buildAuthHeaders(Map.of("apiToken", " glpat-test "));

        assertEquals("glpat-test", headers.get("Private-Token"));
    }

    @Test
    void buildsBearerHeaderWhenConfigured() {
        McpProperties properties = new McpProperties();
        properties.setGitlabAuthHeaderMode("bearer");
        GitLabPrivateTokenAuthStrategy strategy = new GitLabPrivateTokenAuthStrategy(properties);

        Map<String, String> headers = strategy.buildAuthHeaders(Map.of("apiToken", "glpat-test"));

        assertEquals("Bearer glpat-test", headers.get("Authorization"));
    }

    @Test
    void rejectsMissingToken() {
        GitLabPrivateTokenAuthStrategy strategy = new GitLabPrivateTokenAuthStrategy(new McpProperties());

        assertThrows(IllegalArgumentException.class, () -> strategy.buildAuthHeaders(Map.of()));
    }
}
