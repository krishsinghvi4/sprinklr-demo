package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Trims RED sample responses to a configured document limit (most-recent-first order preserved).
 */
public final class RedSampleResponseTrimmer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RedSampleResponseTrimmer() {
    }

    public static String trimToLimit(String content, int limit) {
        if (content == null || content.isBlank() || limit <= 0) {
            return content;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            JsonNode trimmed = trimNode(root, limit);
            if (trimmed == null) {
                return content;
            }
            return OBJECT_MAPPER.writeValueAsString(trimmed);
        } catch (Exception ignored) {
            return content;
        }
    }

    public static int countDocuments(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            return countDocuments(root);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static JsonNode trimNode(JsonNode root, int limit) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        if (root.isArray()) {
            return trimArray(root, limit);
        }
        if (!root.isObject()) {
            return null;
        }
        JsonNode esHits = root.path("hits").path("hits");
        if (esHits.isArray() && esHits.size() > limit) {
            ObjectNode copy = root.deepCopy();
            ArrayNode trimmedHits = OBJECT_MAPPER.createArrayNode();
            for (int i = 0; i < limit && i < esHits.size(); i++) {
                trimmedHits.add(esHits.get(i));
            }
            ((ObjectNode) copy.path("hits")).set("hits", trimmedHits);
            return copy;
        }
        for (String field : new String[] {"documents", "data", "results", "rows"}) {
            JsonNode array = root.path(field);
            if (array.isArray() && array.size() > limit) {
                ObjectNode copy = root.deepCopy();
                ArrayNode trimmed = OBJECT_MAPPER.createArrayNode();
                for (int i = 0; i < limit && i < array.size(); i++) {
                    trimmed.add(array.get(i));
                }
                copy.set(field, trimmed);
                return copy;
            }
        }
        return null;
    }

    private static ArrayNode trimArray(JsonNode root, int limit) {
        if (root.size() <= limit) {
            return null;
        }
        ArrayNode trimmed = OBJECT_MAPPER.createArrayNode();
        for (int i = 0; i < limit && i < root.size(); i++) {
            trimmed.add(root.get(i));
        }
        return trimmed;
    }

    private static int countDocuments(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return 0;
        }
        if (root.isArray()) {
            return root.size();
        }
        JsonNode esHits = root.path("hits").path("hits");
        if (esHits.isArray()) {
            return esHits.size();
        }
        for (String field : new String[] {"documents", "data", "results", "rows"}) {
            JsonNode array = root.path(field);
            if (array.isArray()) {
                return array.size();
            }
        }
        return 0;
    }
}
