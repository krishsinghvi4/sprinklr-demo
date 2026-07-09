package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import com.example.sprinklr.marketplace.domain.model.CrossWorkflowSkillConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectProbeConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialAuthConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialHeaderMode;
import com.example.sprinklr.marketplace.domain.model.MCP.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpToolSelectionConfig;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the MCP server catalog from JSON and exposes catalog-driven auth/connect metadata.
 * OAuth entries must declare an explicit {@code auth.oauth} block with all issuer settings.
 */
@Component
public class McpCatalogLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<McpCatalogEntry> entries;
    private final List<CrossWorkflowSkillConfig> crossWorkflowSkills;
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
            this.crossWorkflowSkills = List.copyOf(parseCrossWorkflowSkills(root.path("crossWorkflowSkills")));
        }
    }

    public List<McpCatalogEntry> getAll() {
        return entries;
    }

    public List<CrossWorkflowSkillConfig> getCrossWorkflowSkills() {
        return crossWorkflowSkills;
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

        String endpointUrl = node.path("endpointUrl").asText();

        String llmSkillPath = node.path("llmSkillPath").asText(null);
        if (llmSkillPath != null && llmSkillPath.isBlank()) {
            llmSkillPath = null;
        }

        return new McpCatalogEntry(
                catalogId,
                node.path("displayName").asText(),
                node.path("description").asText(""),
                endpointUrl,
                node.path("serverIdPrefix").asText(),
                authType,
                authConfig,
                connectMethod,
                fields,
                llmSkillPath,
                parseConnectProbe(node.path("connectProbe"), catalogId),
                parseToolSelection(node.path("toolSelection"))
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
                return new McpAuthConfig(
                        kind,
                        parseRequiredOAuthConfig(authNode.path("oauth"), catalogId),
                        null
                );
            }
            return new McpAuthConfig(
                    McpAuthKind.CREDENTIALS,
                    null,
                    parseCredentialAuthConfig(authNode.path("credentials"), catalogId)
            );
        }

        if (authType.startsWith("OAUTH") || "OAUTH".equals(authType)) {
            throw new IllegalStateException(
                    "OAuth catalog entry '" + catalogId + "' must declare an auth.oauth block "
                            + "with metadataUrl, resource, and scopes.");
        }
        throw new IllegalStateException(
                "Catalog entry '" + catalogId + "' must declare an auth block with kind and configuration.");
    }

    private McpCredentialAuthConfig parseCredentialAuthConfig(JsonNode credentialsNode, String catalogId) {
        if (credentialsNode.isMissingNode() || credentialsNode.isNull()) {
            throw new IllegalStateException(
                    "Credential catalog entry '" + catalogId + "' must declare auth.credentials.");
        }
        String tokenField = credentialsNode.path("tokenField").asText("apiToken");
        McpCredentialHeaderMode headerMode = McpCredentialHeaderMode.valueOf(
                credentialsNode.path("headerMode").asText("BEARER")
        );
        String emailField = credentialsNode.path("emailField").asText(null);
        if (emailField != null && emailField.isBlank()) {
            emailField = null;
        }
        String customHeaderName = credentialsNode.path("customHeaderName").asText(null);
        if (customHeaderName != null && customHeaderName.isBlank()) {
            customHeaderName = null;
        }
        return new McpCredentialAuthConfig(tokenField, headerMode, emailField, customHeaderName);
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
        if (!node.path("credentialFields").isEmpty() || authConfig.isCredentials()) {
            return McpConnectMethod.CREDENTIAL_FORM;
        }
        return McpConnectMethod.CREDENTIAL_FORM;
    }

    private McpConnectProbeConfig parseConnectProbe(JsonNode probeNode, String catalogId) {
        if (probeNode.isMissingNode() || probeNode.isNull()) {
            return null;
        }
        String tool = probeNode.path("tool").asText("").trim();
        if (tool.isBlank()) {
            throw new IllegalStateException("connectProbe.tool is required for catalog entry '" + catalogId + "'");
        }
        String argumentsJson = probeNode.has("arguments")
                ? probeNode.path("arguments").toString()
                : "{}";
        String failureMessage = probeNode.path("failureMessage").asText("").trim();
        if (failureMessage.isBlank()) {
            throw new IllegalStateException(
                    "connectProbe.failureMessage is required for catalog entry '" + catalogId + "'");
        }
        return new McpConnectProbeConfig(tool, argumentsJson, failureMessage);
    }

    private McpToolSelectionConfig parseToolSelection(JsonNode selectionNode) {
        if (selectionNode.isMissingNode() || selectionNode.isNull()) {
            return null;
        }
        List<String> neverSatisfy = new ArrayList<>();
        for (JsonNode toolNode : selectionNode.path("continuationNeverSatisfyTools")) {
            neverSatisfy.add(toolNode.asText());
        }
        Map<String, List<String>> staticDependencyGraph = parseStaticDependencyGraph(
                selectionNode.path("staticDependencyGraph"));
        return new McpToolSelectionConfig(neverSatisfy, staticDependencyGraph);
    }

    private Map<String, List<String>> parseStaticDependencyGraph(JsonNode graphNode) {
        if (graphNode.isMissingNode() || graphNode.isNull() || !graphNode.isObject()) {
            return Map.of();
        }
        Map<String, List<String>> edges = new LinkedHashMap<>();
        graphNode.fields().forEachRemaining(entry -> {
            List<String> prerequisites = new ArrayList<>();
            for (JsonNode prerequisiteNode : entry.getValue()) {
                prerequisites.add(prerequisiteNode.asText());
            }
            edges.put(entry.getKey(), List.copyOf(prerequisites));
        });
        return Map.copyOf(edges);
    }

    private List<CrossWorkflowSkillConfig> parseCrossWorkflowSkills(JsonNode skillsNode) {
        if (skillsNode.isMissingNode() || !skillsNode.isArray()) {
            return List.of();
        }
        List<CrossWorkflowSkillConfig> parsed = new ArrayList<>();
        for (JsonNode skillNode : skillsNode) {
            Set<String> requiredPrefixes = new LinkedHashSet<>();
            for (JsonNode prefixNode : skillNode.path("requiredPrefixes")) {
                requiredPrefixes.add(prefixNode.asText());
            }
            parsed.add(new CrossWorkflowSkillConfig(
                    skillNode.path("id").asText(),
                    skillNode.path("title").asText(),
                    skillNode.path("skillPath").asText(),
                    requiredPrefixes
            ));
        }
        return parsed;
    }
}
