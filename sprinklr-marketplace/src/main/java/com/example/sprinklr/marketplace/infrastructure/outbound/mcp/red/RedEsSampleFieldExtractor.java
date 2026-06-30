package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Extracts dotted field paths from RED Elasticsearch sample responses for filter guidance.
 */
public final class RedEsSampleFieldExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RedEsSampleFieldExtractor() {
    }

    public static RedEsSampleFieldCatalog parseCatalog(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return RedEsSampleFieldCatalog.empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawContent);
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                return RedEsSampleFieldCatalog.empty();
            }
            Set<String> paths = new LinkedHashSet<>();
            Set<String> arrayPaths = new LinkedHashSet<>();
            for (JsonNode hit : hits) {
                flatten(hit.path("_source"), "", paths, arrayPaths);
            }
            return new RedEsSampleFieldCatalog(paths, arrayPaths);
        } catch (Exception ignored) {
            return RedEsSampleFieldCatalog.empty();
        }
    }

    private static void flatten(JsonNode node, String prefix, Set<String> paths, Set<String> arrayPaths) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flatten(entry.getValue(), path, paths, arrayPaths);
            });
            return;
        }
        if (node.isArray()) {
            if (!prefix.isEmpty()) {
                paths.add(prefix);
                arrayPaths.add(prefix);
            }
            return;
        }
        if (!prefix.isEmpty()) {
            paths.add(prefix);
        }
    }
}
