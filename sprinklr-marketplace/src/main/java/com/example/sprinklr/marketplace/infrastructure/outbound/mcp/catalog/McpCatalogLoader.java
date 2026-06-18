package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import com.example.sprinklr.marketplace.domain.model.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.McpCredentialField;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.AtlassianOAuthAuthStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.BasicEmailTokenAuthStrategy;
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
 * Supports legacy entries (authType only) and the extended auth block schema.
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
        McpAuthConfig authConfig = parseAuthConfig(node, authType);
        McpConnectMethod connectMethod = parseConnectMethod(node, authConfig);

        return new McpCatalogEntry(
                node.path("id").asText(),
                node.path("displayName").asText(),
                node.path("description").asText(""),
                node.path("endpointUrl").asText(),
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

    private McpAuthConfig parseAuthConfig(JsonNode node, String authType) {
        JsonNode authNode = node.path("auth");
        if (!authNode.isMissingNode() && !authNode.isNull()) {
            McpAuthKind kind = McpAuthKind.valueOf(authNode.path("kind").asText("CREDENTIALS"));
            if (kind == McpAuthKind.OAUTH) {
                JsonNode oauthNode = authNode.path("oauth");
                String resource = oauthNode.has("resource") && !oauthNode.path("resource").asText("").isBlank()
                        ? oauthNode.path("resource").asText()
                        : properties.getOauthResource();
                return new McpAuthConfig(kind, new McpOAuthCatalogConfig(
                        oauthNode.path("providerKey").asText(node.path("id").asText()),
                        oauthNode.path("metadataUrl").asText(properties.getOauthMetadataUrl()),
                        resource,
                        oauthNode.path("scopes").asText(properties.getOauthScopes()),
                        oauthNode.path("useDcr").asBoolean(true),
                        oauthNode.path("usePkce").asBoolean(true),
                        oauthNode.path("includeResourceParam").asBoolean(true),
                        oauthNode.path("requiresRefreshToken").asBoolean(true)
                ));
            }
            return new McpAuthConfig(McpAuthKind.CREDENTIALS, null);
        }

        // Legacy inference from authType for backward compatibility.
        if (AtlassianOAuthAuthStrategy.AUTH_TYPE.equals(authType)
                || authType.startsWith("OAUTH_")) {
            return new McpAuthConfig(McpAuthKind.OAUTH, new McpOAuthCatalogConfig(
                    inferProviderKey(authType, node.path("id").asText()),
                    properties.getOauthMetadataUrl(),
                    properties.getOauthResource(),
                    properties.getOauthScopes(),
                    true,
                    true,
                    true,
                    true
            ));
        }
        return new McpAuthConfig(McpAuthKind.CREDENTIALS, null);
    }

    private McpConnectMethod parseConnectMethod(JsonNode node, McpAuthConfig authConfig) {
        String explicit = node.path("connectMethod").asText(null);
        if (explicit != null && !explicit.isBlank()) {
            return McpConnectMethod.valueOf(explicit);
        }
        if (authConfig.isOAuth()) {
            return McpConnectMethod.OAUTH_REDIRECT;
        }
        if (BasicEmailTokenAuthStrategy.AUTH_TYPE.equals(node.path("authType").asText())
                || !node.path("credentialFields").isEmpty()) {
            return McpConnectMethod.CREDENTIAL_FORM;
        }
        return McpConnectMethod.CREDENTIAL_FORM;
    }

    private String inferProviderKey(String authType, String catalogId) {
        if (AtlassianOAuthAuthStrategy.AUTH_TYPE.equals(authType)) {
            return "atlassian";
        }
        if (authType.startsWith("OAUTH_")) {
            return authType.substring("OAUTH_".length()).toLowerCase();
        }
        return catalogId;
    }
}
