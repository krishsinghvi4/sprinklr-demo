package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import com.example.sprinklr.marketplace.domain.model.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.McpCredentialField;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.BasicEmailTokenAuthStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.GitLabPrivateTokenAuthStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads the MCP server catalog from JSON and exposes catalog-driven auth/connect metadata.
 * OAuth entries must declare an explicit {@code auth.oauth} block with all issuer settings.
 */
@Component
public class McpCatalogLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<McpCatalogEntry> entries;
    private final McpProperties properties;

    public McpCatalogLoader(McpProperties properties, ResourceLoader resourceLoader) throws IOException {
        this.properties = properties;
        Resource resource = resourceLoader.getResource(properties.getCatalogPath());
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            List<McpCatalogEntry> loaded = new ArrayList<>();
            for (JsonNode serverNode : root.path("servers")) {
                loaded.add(parseEntry(serverNode));
            }
            this.entries = List.copyOf(loaded);
        }
    }

    public List<McpCatalogEntry> getAll() {
        return entries;
    }

    public Optional<McpCatalogEntry> findById(String id) {
        return entries.stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst();
    }

    private McpCatalogEntry parseEntry(JsonNode node) {
        List<McpCredentialField> fields = parseCredentialFields(node.path("credentialFields"));
        String authType = node.path("authType").asText();
        String catalogId = node.path("id").asText();
        McpAuthConfig authConfig = parseAuthConfig(node, authType, catalogId);
        McpConnectMethod connectMethod = parseConnectMethod(node, authConfig);

        String endpointUrl = resolveEndpointUrl(catalogId, node.path("endpointUrl").asText());

        return new McpCatalogEntry(
                catalogId,
                node.path("displayName").asText(),
                node.path("description").asText(""),
                endpointUrl,
                node.path("serverIdPrefix").asText(),
                authType,
                authConfig,
                connectMethod,
                fields
        );
    }

    private List<McpCredentialField> parseCredentialFields(JsonNode fieldsNode) {
        List<McpCredentialField> fields = new ArrayList<>();
        for (JsonNode fieldNode : fieldsNode) {
            fields.add(new McpCredentialField(
                    fieldNode.path("key").asText(),
                    fieldNode.path("label").asText(),
                    fieldNode.path("type").asText("text"),
                    fieldNode.path("required").asBoolean(true)
            ));
        }
        return fields;
    }

    private McpAuthConfig parseAuthConfig(JsonNode node, String authType, String catalogId) {
        JsonNode authNode = node.path("auth");
        if (!authNode.isMissingNode() && !authNode.isNull()) {
            McpAuthKind kind = McpAuthKind.valueOf(authNode.path("kind").asText("CREDENTIALS"));
            if (kind == McpAuthKind.OAUTH) {
                return new McpAuthConfig(kind, parseRequiredOAuthConfig(authNode.path("oauth"), catalogId));
            }
            return new McpAuthConfig(McpAuthKind.CREDENTIALS, null);
        }

        if (authType.startsWith("OAUTH_")) {
            throw new IllegalStateException(
                    "OAuth catalog entry '" + catalogId + "' must declare an auth.oauth block "
                            + "with metadataUrl, resource, and scopes.");
        }
        return new McpAuthConfig(McpAuthKind.CREDENTIALS, null);
    }

    private McpOAuthCatalogConfig parseRequiredOAuthConfig(JsonNode oauthNode, String catalogId) {
        String metadataUrl = requireOAuthField(oauthNode, "metadataUrl", catalogId);
        String resource = requireOAuthField(oauthNode, "resource", catalogId);
        String scopes = requireOAuthField(oauthNode, "scopes", catalogId);
        return new McpOAuthCatalogConfig(
                oauthNode.path("providerKey").asText(catalogId),
                metadataUrl,
                resource,
                scopes,
                oauthNode.path("useDcr").asBoolean(true),
                oauthNode.path("usePkce").asBoolean(true),
                oauthNode.path("includeResourceParam").asBoolean(true),
                oauthNode.path("requiresRefreshToken").asBoolean(true)
        );
    }

    private static String requireOAuthField(JsonNode oauthNode, String fieldName, String catalogId) {
        String value = oauthNode.path(fieldName).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "OAuth catalog entry '" + catalogId + "' is missing auth.oauth." + fieldName);
        }
        return value;
    }

    private McpConnectMethod parseConnectMethod(JsonNode node, McpAuthConfig authConfig) {
        String explicit = node.path("connectMethod").asText(null);
        if (explicit != null && !explicit.isBlank()) {
            return McpConnectMethod.valueOf(explicit);
        }
        if (authConfig.isOAuth()) {
            return McpConnectMethod.OAUTH_REDIRECT;
        }
        String authType = node.path("authType").asText();
        if (BasicEmailTokenAuthStrategy.AUTH_TYPE.equals(authType)
                || GitLabPrivateTokenAuthStrategy.AUTH_TYPE.equals(authType)
                || !node.path("credentialFields").isEmpty()) {
            return McpConnectMethod.CREDENTIAL_FORM;
        }
        return McpConnectMethod.CREDENTIAL_FORM;
    }

    private String resolveEndpointUrl(String catalogId, String catalogEndpointUrl) {
        if ("gitlab-mcp".equals(catalogId)) {
            String configured = properties.getGitlabMcpEndpointUrl();
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
        }
        return catalogEndpointUrl;
    }
}
