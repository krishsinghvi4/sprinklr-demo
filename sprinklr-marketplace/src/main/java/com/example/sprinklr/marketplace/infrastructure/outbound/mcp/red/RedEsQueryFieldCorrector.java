package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Rewrites Elasticsearch filter field names using paths discovered from the sample response.
 * Works for any index — no field names are hardcoded.
 */
final class RedEsQueryFieldCorrector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RedEsQueryFieldCorrector() {
    }

    static String correctQueryFields(String queryJson, RedEsSampleFieldCatalog catalog) {
        if (queryJson == null || queryJson.isBlank() || catalog == null || catalog.isEmpty()) {
            return queryJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(queryJson);
            if (!root.isObject()) {
                return queryJson;
            }
            boolean changed = correctNode((ObjectNode) root, catalog);
            return changed ? OBJECT_MAPPER.writeValueAsString(root) : queryJson;
        } catch (Exception ignored) {
            return queryJson;
        }
    }

    private static boolean correctNode(ObjectNode node, RedEsSampleFieldCatalog catalog) {
        boolean changed = false;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if ("term".equals(key) && value.isObject()) {
                changed |= rewriteFilterClause(node, (ObjectNode) value, catalog, false);
            } else if ("terms".equals(key) && value.isObject()) {
                changed |= rewriteFilterClause(node, (ObjectNode) value, catalog, true);
            } else if (value.isObject()) {
                changed |= correctNode((ObjectNode) value, catalog);
            } else if (value.isArray()) {
                for (JsonNode child : value) {
                    if (child.isObject()) {
                        changed |= correctNode((ObjectNode) child, catalog);
                    }
                }
            }
        }
        return changed;
    }

    private static boolean rewriteFilterClause(
            ObjectNode clause,
            ObjectNode fieldsNode,
            RedEsSampleFieldCatalog catalog,
            boolean alreadyTerms
    ) {
        Iterator<Map.Entry<String, JsonNode>> fields = fieldsNode.fields();
        if (!fields.hasNext()) {
            return false;
        }
        Map.Entry<String, JsonNode> entry = fields.next();
        String queryField = entry.getKey();
        Optional<String> resolved = catalog.resolve(queryField);
        if (resolved.isEmpty()) {
            return false;
        }
        String resolvedPath = resolved.get();
        if (resolvedPath.equals(queryField) && (!catalog.isArrayField(resolvedPath) || alreadyTerms)) {
            return false;
        }

        ArrayNode values = toValueArray(entry.getValue());
        clause.remove(alreadyTerms ? "terms" : "term");
        ObjectNode terms = clause.putObject("terms");
        terms.set(resolvedPath, values);
        return true;
    }

    private static ArrayNode toValueArray(JsonNode value) {
        ArrayNode values = OBJECT_MAPPER.createArrayNode();
        if (value.isArray()) {
            values.addAll((ArrayNode) value);
        } else {
            values.add(value);
        }
        return values;
    }
}
