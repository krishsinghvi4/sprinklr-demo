package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prepends user-configured RED query allowlists onto Elasticsearch and Mongo tool descriptions
 * so the tool router and agent LLM see them when choosing a backend.
 */
@Component
public class RedQueryPreferencesToolDescriptionAugmenter {

    private static final String RED_PREFIX = "red.";
    private static final Set<String> ES_QUERY_TOOLS = Set.of(
            RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
            RedSampleQueryCacheKeyBuilder.ES_EXECUTE_TOOL);
    private static final Set<String> MONGO_QUERY_TOOLS = Set.of(
            RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
            RedSampleQueryCacheKeyBuilder.MONGO_EXECUTE_TOOL);

    private final McpRegistryPort registryPort;

    public RedQueryPreferencesToolDescriptionAugmenter(McpRegistryPort registryPort) {
        this.registryPort = registryPort;
    }

    public List<McpTool> augment(String userId, List<McpTool> tools) {
        if (userId == null || userId.isBlank() || tools == null || tools.isEmpty()) {
            return tools == null ? List.of() : tools;
        }

        Map<String, RedQueryPreferences> preferencesByConnectionId = new HashMap<>();
        return tools.stream()
                .map(tool -> augmentTool(userId, tool, preferencesByConnectionId))
                .toList();
    }

    private McpTool augmentTool(
            String userId,
            McpTool tool,
            Map<String, RedQueryPreferences> preferencesByConnectionId
    ) {
        if (!tool.name().startsWith(RED_PREFIX)) {
            return tool;
        }

        String bareName = bareToolName(tool.name());
        boolean esTool = ES_QUERY_TOOLS.contains(bareName);
        boolean mongoTool = MONGO_QUERY_TOOLS.contains(bareName);
        if (!esTool && !mongoTool) {
            return tool;
        }

        RedQueryPreferences preferences = preferencesByConnectionId.computeIfAbsent(
                tool.serverId(),
                connectionId -> registryPort.findRedQueryPreferences(userId, connectionId).orElse(null));
        if (preferences == null || preferences.isEmpty()) {
            return tool;
        }

        String prefix = esTool
                ? formatElasticsearchPrefix(preferences)
                : formatMongoPrefix(preferences);
        if (prefix == null) {
            return tool;
        }

        return new McpTool(
                tool.name(),
                prefix + tool.description(),
                tool.serverId(),
                tool.inputSchemaJson());
    }

    private static String formatElasticsearchPrefix(RedQueryPreferences preferences) {
        if (preferences.elasticsearchServerTypes().isEmpty()) {
            return null;
        }
        return "User allowlist (Elasticsearch-only serverTypes): "
                + String.join(", ", preferences.elasticsearchServerTypes())
                + ". ";
    }

    private static String formatMongoPrefix(RedQueryPreferences preferences) {
        if (preferences.mongoServerTypes().isEmpty()) {
            return null;
        }
        String entries = preferences.mongoServerTypes().stream()
                .map(entry -> entry.serverType()
                        + "/"
                        + String.join(",", entry.collectionNames()))
                .collect(Collectors.joining("; "));
        return "User allowlist (Mongo-only): " + entries + ". ";
    }

    private static String bareToolName(String fullyQualifiedName) {
        int dot = fullyQualifiedName.lastIndexOf('.');
        return dot >= 0 ? fullyQualifiedName.substring(dot + 1) : fullyQualifiedName;
    }
}
