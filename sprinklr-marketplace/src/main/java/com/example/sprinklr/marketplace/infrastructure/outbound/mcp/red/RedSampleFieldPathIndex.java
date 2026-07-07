package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Extracts dotted filter field paths from RED sample query JSON and appends a path index for the LLM.
 * Schema-agnostic: walks whatever _source / document bodies are present.
 */
public final class RedSampleFieldPathIndex {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_PATHS = 100;

    private RedSampleFieldPathIndex() {
    }

    public static String appendIndex(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return rawContent;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawContent);
            Map<String, String> paths = collectPaths(root);
            if (paths.isEmpty()) {
                return rawContent;
            }
            return rawContent + formatAppendix(paths);
        } catch (Exception ignored) {
            return rawContent;
        }
    }

    private static Map<String, String> collectPaths(JsonNode root) {
        Map<String, String> paths = new TreeMap<>();
        for (JsonNode document : findDocuments(root)) {
            walk(document, "", paths);
        }
        return paths;
    }

    private static List<JsonNode> findDocuments(JsonNode root) {
        List<JsonNode> documents = new ArrayList<>();
        collectElasticsearchSources(root, documents);
        if (!documents.isEmpty()) {
            return documents;
        }
        if (looksLikeElasticsearchResponse(root)) {
            return documents;
        }
        collectMongoDocuments(root, documents);
        if (!documents.isEmpty()) {
            return documents;
        }
        if (root.isArray()) {
            for (JsonNode element : root) {
                if (element.isObject()) {
                    documents.add(element);
                }
            }
        } else if (root.isObject() && !isHitWrapper(root)) {
            documents.add(root);
        }
        return documents;
    }

    private static boolean looksLikeElasticsearchResponse(JsonNode root) {
        if (!root.isObject()) {
            return false;
        }
        JsonNode hits = root.path("hits");
        return hits.isObject() && (hits.has("hits") || hits.has("total"));
    }

    private static void collectElasticsearchSources(JsonNode node, List<JsonNode> documents) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isObject() && node.has("_source") && node.get("_source").isObject()) {
            documents.add(node.get("_source"));
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectElasticsearchSources(entry.getValue(), documents));
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                collectElasticsearchSources(element, documents);
            }
        }
    }

    private static void collectMongoDocuments(JsonNode node, List<JsonNode> documents) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        for (String field : List.of("documents", "data", "results", "rows")) {
            JsonNode candidate = node.path(field);
            if (candidate.isArray()) {
                for (JsonNode element : candidate) {
                    if (element.isObject()) {
                        documents.add(element);
                    }
                }
                if (!documents.isEmpty()) {
                    return;
                }
            }
        }
    }

    private static boolean isHitWrapper(JsonNode node) {
        return node.has("_id") || node.has("_index") || node.has("_score");
    }

    private static void walk(JsonNode node, String prefix, Map<String, String> paths) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (paths.size() >= MAX_PATHS) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isObject()) {
                    walk(value, path, paths);
                } else if (value.isArray()) {
                    handleArray(value, path, paths);
                } else {
                    putPath(paths, path, leafType(value));
                }
            });
            return;
        }
        if (node.isArray() && !prefix.isEmpty()) {
            handleArray(node, prefix, paths);
        }
    }

    private static void handleArray(JsonNode array, String path, Map<String, String> paths) {
        if (array.isEmpty()) {
            putPath(paths, path, "array");
            return;
        }
        boolean objectElements = false;
        for (JsonNode element : array) {
            if (element.isObject()) {
                objectElements = true;
                walk(element, path, paths);
            }
        }
        if (!objectElements) {
            putPath(paths, path, "array");
        }
    }

    private static void putPath(Map<String, String> paths, String path, String type) {
        if (path == null || path.isBlank() || paths.size() >= MAX_PATHS) {
            return;
        }
        paths.putIfAbsent(path, type);
    }

    private static String leafType(JsonNode value) {
        if (value.isTextual()) {
            return "string";
        }
        if (value.isNumber()) {
            return "number";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isNull()) {
            return "null";
        }
        return "value";
    }

    private static String formatAppendix(Map<String, String> paths) {
        StringBuilder appendix = new StringBuilder();
        appendix.append("\n\n---\n");
        appendix.append("### Filter field paths (from this sample — use these exact dotted paths in execute)\n");
        appendix.append("Schema differs per serverType/index — use only paths from this list, not field names from ");
        appendix.append("examples or prior turns. Do not use Elasticsearch hit metadata (_id, _index). ");
        appendix.append("For object arrays, use parent.arrayField.leaf (never [0]).\n\n");
        appendix.append("**All paths:**\n");
        for (Map.Entry<String, String> entry : paths.entrySet()) {
            appendix.append("- ").append(entry.getKey()).append(" (").append(entry.getValue()).append(")\n");
        }

        List<String> idLikePaths = paths.entrySet().stream()
                .filter(entry -> isIdLikePath(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        if (!idLikePaths.isEmpty()) {
            appendix.append("\n**Paths matching id / segment (for user-provided IDs):**\n");
            for (String path : idLikePaths) {
                appendix.append("- ").append(path).append(" (").append(paths.get(path)).append(")\n");
            }
        }
        return appendix.toString();
    }

    private static boolean isIdLikePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        String leaf = dot >= 0 ? path.substring(dot + 1) : path;
        String lower = leaf.toLowerCase(Locale.ROOT);
        return lower.contains("id") || lower.contains("segment");
    }
}
