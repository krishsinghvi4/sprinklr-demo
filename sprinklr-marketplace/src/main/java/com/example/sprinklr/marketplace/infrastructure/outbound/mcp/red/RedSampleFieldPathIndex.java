package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Extracts dotted filter field paths from RED sample query JSON and prepends a path index for the LLM.
 * Schema-agnostic: walks whatever _source / document bodies are present.
 */
public final class RedSampleFieldPathIndex {

    private static final Logger log = LoggerFactory.getLogger(RedSampleFieldPathIndex.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_PATHS = 100;
    private static final int MAX_LOGGED_PATHS = 20;
    private static final int MAX_EXAMPLE_CHARS = 100;
    private static final int MAX_ARRAY_ELEMENTS = 5;

    private RedSampleFieldPathIndex() {
    }

    public static boolean hasFilterablePaths(String rawContent) {
        return !collectFilterPaths(rawContent).isEmpty();
    }

    public static Map<String, String> extractPaths(String rawContent) {
        Map<String, String> types = new TreeMap<>();
        for (Map.Entry<String, RedSampleFilterPath> entry : collectFilterPaths(rawContent).entrySet()) {
            types.put(entry.getKey(), entry.getValue().type());
        }
        return types;
    }

    public static RedSampleSchemaIndex indexForLlm(String rawContent) {
        return indexForLlm(rawContent, -1, -1);
    }

    public static RedSampleSchemaIndex indexForLlm(String rawContent, int documentLimit, int documentsReturned) {
        return indexForLlm(rawContent, rawContent, documentLimit, documentsReturned);
    }

    public static RedSampleSchemaIndex indexForLlm(
            String schemaContent,
            String displayContent,
            int schemaDiscoveryLimit,
            int documentsReturned
    ) {
        if (schemaContent == null || schemaContent.isBlank()) {
            return new RedSampleSchemaIndex(displayContent, Map.of(), displayContent, 0);
        }
        try {
            JsonNode schemaRoot = OBJECT_MAPPER.readTree(schemaContent);
            List<JsonNode> documents = findDocuments(schemaRoot);
            Map<String, RedSampleFilterPath> paths = collectPaths(schemaRoot);
            String safeDisplayContent = displayContent == null || displayContent.isBlank()
                    ? schemaContent
                    : displayContent;
            String indexedContent = paths.isEmpty()
                    ? safeDisplayContent
                    : formatAppendix(paths) + "\n\n---\n\n" + safeDisplayContent;
            int appendixChars = paths.isEmpty() ? 0 : indexedContent.length() - safeDisplayContent.length();
            logSchema(paths, documents.size(), appendixChars, schemaDiscoveryLimit, documentsReturned);
            return new RedSampleSchemaIndex(safeDisplayContent, paths, indexedContent, documents.size());
        } catch (Exception ignored) {
            log.warn("[RedSampleSchema] Failed to parse sample JSON for path extraction");
            return new RedSampleSchemaIndex(displayContent, Map.of(), displayContent, 0);
        }
    }

    public static String appendIndex(String rawContent) {
        return indexForLlm(rawContent).indexedContent();
    }

    private static Map<String, RedSampleFilterPath> collectFilterPaths(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawContent);
            return collectPaths(root);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static void logSchema(
            Map<String, RedSampleFilterPath> paths,
            int documentCount,
            int appendixChars,
            int documentLimit,
            int documentsReturned
    ) {
        if (paths.isEmpty()) {
            if (documentCount > 0) {
                log.warn("[RedSampleSchema] documentCount={} pathCount=0 — hits exist but no walkable _source/body",
                        documentCount);
            } else {
                log.warn("[RedSampleSchema] pathCount=0 documentCount=0 — empty schema");
            }
            return;
        }
        String pathSummary = formatPathListForLog(paths);
        if (documentLimit > 0 && documentsReturned >= 0) {
            log.info(
                    "[RedSampleSchema] pathCount={} documentCount={} documentLimit={} documentsReturned={} "
                            + "appendixChars={} paths=[{}]",
                    paths.size(),
                    documentCount,
                    documentLimit,
                    documentsReturned,
                    appendixChars,
                    pathSummary
            );
        } else {
            log.info(
                    "[RedSampleSchema] pathCount={} documentCount={} appendixChars={} paths=[{}]",
                    paths.size(),
                    documentCount,
                    appendixChars,
                    pathSummary
            );
        }
    }

    private static String formatPathListForLog(Map<String, RedSampleFilterPath> paths) {
        List<String> keys = new ArrayList<>(paths.keySet());
        if (keys.size() <= MAX_LOGGED_PATHS) {
            return String.join(", ", keys);
        }
        return String.join(", ", keys.subList(0, MAX_LOGGED_PATHS)) + " ...(" + (keys.size() - MAX_LOGGED_PATHS) + " more)";
    }

    private static Map<String, RedSampleFilterPath> collectPaths(JsonNode root) {
        Map<String, RedSampleFilterPath> paths = new TreeMap<>();
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
        if (looksLikeMongoResponse(root)) {
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

    private static boolean looksLikeMongoResponse(JsonNode root) {
        if (!root.isObject()) {
            return false;
        }
        for (String field : List.of("documents", "data", "results", "rows")) {
            if (root.path(field).isArray()) {
                return true;
            }
        }
        return false;
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

    private static void walk(JsonNode node, String prefix, Map<String, RedSampleFilterPath> paths) {
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
                    putPath(paths, path, leafType(value), value);
                }
            });
            return;
        }
        if (node.isArray() && !prefix.isEmpty()) {
            handleArray(node, prefix, paths);
        }
    }

    private static void handleArray(JsonNode array, String path, Map<String, RedSampleFilterPath> paths) {
        if (array.isEmpty()) {
            putPath(paths, path, "array", array);
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
            putPath(paths, path, "array", array);
        }
    }

    private static void putPath(
            Map<String, RedSampleFilterPath> paths,
            String path,
            String type,
            JsonNode exampleNode
    ) {
        if (path == null || path.isBlank() || paths.size() >= MAX_PATHS) {
            return;
        }
        paths.putIfAbsent(path, new RedSampleFilterPath(type, formatExample(exampleNode)));
    }

    private static String formatExample(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return truncate(value.asText(), MAX_EXAMPLE_CHARS);
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        if (value.isArray()) {
            return truncate(formatArrayExample(value), MAX_EXAMPLE_CHARS);
        }
        return null;
    }

    private static String formatArrayExample(JsonNode array) {
        List<JsonNode> elements = new ArrayList<>();
        array.forEach(elements::add);
        int limit = Math.min(elements.size(), MAX_ARRAY_ELEMENTS);
        List<JsonNode> slice = elements.subList(0, limit);
        try {
            String json = OBJECT_MAPPER.writeValueAsString(slice);
            if (elements.size() > limit) {
                return json.substring(0, json.length() - 1) + ", ...]";
            }
            return json;
        } catch (Exception ignored) {
            return "[...]";
        }
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars - 3) + "...";
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

    private static String formatAppendix(Map<String, RedSampleFilterPath> paths) {
        StringBuilder appendix = new StringBuilder();
        appendix.append("### Filter field paths (from this sample — use these exact dotted paths in execute)\n");
        appendix.append("Schema differs per serverType/index. **WARNING: These are ALL known filterable fields for ");
        appendix.append("this scope. Do NOT invent or guess field names. Any field not in this list does not exist ");
        appendix.append("in this schema — if the user requests a filter on an unlisted field, tell them it is ");
        appendix.append("unavailable rather than guessing.** Do not use Elasticsearch hit metadata (_id, _index). ");
        appendix.append("For object arrays, use parent.arrayField.leaf (never [0]).\n\n");
        appendix.append("**All paths:**\n");
        for (Map.Entry<String, RedSampleFilterPath> entry : paths.entrySet()) {
            appendix.append("- ").append(formatPathLine(entry.getKey(), entry.getValue())).append('\n');
        }

        List<String> idLikePaths = paths.entrySet().stream()
                .filter(entry -> isIdLikePath(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        if (!idLikePaths.isEmpty()) {
            appendix.append("\n**Paths matching id / segment (for user-provided IDs):**\n");
            for (String path : idLikePaths) {
                appendix.append("- ").append(formatPathLine(path, paths.get(path))).append('\n');
            }
        }
        return appendix.toString();
    }

    private static String formatPathLine(String path, RedSampleFilterPath filterPath) {
        StringBuilder line = new StringBuilder();
        line.append(path).append(" (").append(filterPath.type()).append(')');
        if (filterPath.example() != null && !filterPath.example().isBlank()) {
            line.append(" — e.g. ").append(formatExampleForDisplay(filterPath));
        }
        return line.toString();
    }

    private static String formatExampleForDisplay(RedSampleFilterPath filterPath) {
        String example = filterPath.example();
        if ("string".equals(filterPath.type())) {
            return "\"" + example + "\"";
        }
        return example;
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
