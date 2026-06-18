package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GitLab MCP auth for zereight/gitlab-mcp with REMOTE_AUTHORIZATION=true.
 * Sends the user's PAT as Private-Token (default) or Bearer, matching the MCP server's expectations.
 */
@Component
public class GitLabPrivateTokenAuthStrategy implements McpAuthStrategy {

    public static final String AUTH_TYPE = "GITLAB_PRIVATE_TOKEN";

    private final McpProperties properties;

    public GitLabPrivateTokenAuthStrategy(McpProperties properties) {
        this.properties = properties;
    }

    @Override
    public String authType() {
        return AUTH_TYPE;
    }

    @Override
    public Map<String, String> buildAuthHeaders(Map<String, String> credentials) {
        String apiToken = credentials.get("apiToken");
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("apiToken is required for GitLab MCP auth");
        }

        if ("bearer".equalsIgnoreCase(properties.getGitlabAuthHeaderMode())) {
            return Map.of("Authorization", "Bearer " + apiToken.trim());
        }
        return Map.of("Private-Token", apiToken.trim());
    }
}
