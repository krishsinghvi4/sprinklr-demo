package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpCredentialField;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
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
 * Loads the MCP server catalog from a JSON resource and exposes it to services.
 * The catalog powers marketplace listings and connection metadata (endpoint URL,
 * auth type, and serverIdPrefix used to namespace tools).
 */
@Component
public class McpCatalogLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<McpCatalogEntry> entries;

    /**
     * Reads the catalog file configured in {@link McpProperties#getCatalogPath()}.
     */
    public McpCatalogLoader(McpProperties properties, ResourceLoader resourceLoader) throws IOException {
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

    /**
     * @return immutable list of all catalog entries used by the marketplace UI.
     */
    public List<McpCatalogEntry> getAll() {
        return entries;
    }

    /**
     * Finds a catalog entry by its stable ID (e.g., "atlassian-jira").
     */
    public Optional<McpCatalogEntry> findById(String id) {
        return entries.stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst();
    }

    /**
     * Converts a JSON node from the catalog into a domain entry.
     */
    private McpCatalogEntry parseEntry(JsonNode node) {
        List<McpCredentialField> fields = new ArrayList<>();
        for (JsonNode fieldNode : node.path("credentialFields")) {
            fields.add(new McpCredentialField(
                    fieldNode.path("key").asText(),
                    fieldNode.path("label").asText(),
                    fieldNode.path("type").asText("text"),
                    fieldNode.path("required").asBoolean(true)
            ));
        }

        return new McpCatalogEntry(
                node.path("id").asText(),
                node.path("displayName").asText(),
                node.path("description").asText(""),
                node.path("endpointUrl").asText(),
                node.path("serverIdPrefix").asText(),
                node.path("authType").asText(),
                fields
        );
    }
}
