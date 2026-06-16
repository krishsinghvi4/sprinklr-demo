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

@Component
public class McpCatalogLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<McpCatalogEntry> entries;

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

    public List<McpCatalogEntry> getAll() {
        return entries;
    }

    public Optional<McpCatalogEntry> findById(String id) {
        return entries.stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst();
    }

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
