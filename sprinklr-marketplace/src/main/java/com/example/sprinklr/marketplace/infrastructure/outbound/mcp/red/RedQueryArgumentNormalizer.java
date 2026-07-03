package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpToolArgumentNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Repairs common LLM mistakes when calling RED Elasticsearch execute tools.
 */
@Component
public class RedQueryArgumentNormalizer implements McpToolArgumentNormalizer {

    private static final Logger log = LoggerFactory.getLogger(RedQueryArgumentNormalizer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RED_PREFIX = "red";
    private static final Set<String> ELASTICSEARCH_EXECUTE_TOOLS = Set.of(
            "red_execute_elastic_search_query",
            "red_execute_audit_log_elasticsearch_query"
    );
    private static final Set<String> HOISTABLE_FROM_QUERY = Set.of(
            "sort", "size", "from", "_source", "aggs", "track_total_hits"
    );

    private final RedEsSampleFieldContext sampleFieldContext;

    public RedQueryArgumentNormalizer(RedEsSampleFieldContext sampleFieldContext) {
        this.sampleFieldContext = sampleFieldContext;
    }

    @Override
    public boolean supports(McpCatalogEntry entry, String toolName) {
        return RED_PREFIX.equals(entry.serverIdPrefix()) && supports(toolName);
    }

    public boolean supports(String toolName) {
        String bare = bareToolName(toolName);
        return bare != null && ELASTICSEARCH_EXECUTE_TOOLS.contains(bare);
    }

    @Override
    public String normalize(McpCatalogEntry entry, String toolName, String argumentsJson, String connectionId) {
        return normalize(toolName, argumentsJson);
    }

    public String normalize(String toolName, String argumentsJson) {
        if (!supports(toolName) || argumentsJson == null || argumentsJson.isBlank()) {
            return argumentsJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            if (!root.isObject()) {
                return argumentsJson;
            }
            ObjectNode args = (ObjectNode) root;
            JsonNode queryNode = args.get("query");
            if (queryNode != null && queryNode.isObject()) {
                args.put("query", OBJECT_MAPPER.writeValueAsString(queryNode));
                log.info("[MCP] Normalized RED ES tool={} stringified query object", toolName);
            }
            JsonNode queryStringNode = args.get("query");
            if (queryStringNode != null && queryStringNode.isTextual()) {
                String original = queryStringNode.asText();
                String current = wrapQueryBodyIfNeeded(original);
                if (!current.equals(original)) {
                    log.info("[MCP] Normalized RED ES tool={} wrapped query body under top-level query key", toolName);
                }
                String hoisted = hoistNonQueryClausesFromQueryObject(current);
                if (!hoisted.equals(current)) {
                    current = hoisted;
                    log.info("[MCP] Normalized RED ES tool={} hoisted sort/size (and siblings) out of query object",
                            toolName);
                }
                if (!current.equals(original)) {
                    args.put("query", current);
                }
                sampleFieldContext.current().ifPresent(catalog -> {
                    String queryForCorrection = args.get("query").asText();
                    String fixed = RedEsQueryFieldCorrector.correctQueryFields(queryForCorrection, catalog);
                    if (!fixed.equals(queryForCorrection)) {
                        args.put("query", fixed);
                        log.info("[MCP] Normalized RED ES tool={} corrected filter fields using sample schema",
                                toolName);
                    }
                });
            }
            return OBJECT_MAPPER.writeValueAsString(args);
        } catch (Exception exception) {
            log.warn("[MCP] Failed to normalize RED ES args for tool={}: {}", toolName, exception.getMessage());
            return argumentsJson;
        }
    }

    static String hoistNonQueryClausesFromQueryObject(String queryJson) {
        if (queryJson == null || queryJson.isBlank()) {
            return queryJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(queryJson);
            if (!root.isObject()) {
                return queryJson;
            }
            ObjectNode rootObj = (ObjectNode) root;
            JsonNode queryNode = rootObj.get("query");
            if (queryNode == null || !queryNode.isObject()) {
                return queryJson;
            }
            ObjectNode queryObj = (ObjectNode) queryNode;
            boolean changed = false;
            Iterator<Map.Entry<String, JsonNode>> fields = queryObj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (HOISTABLE_FROM_QUERY.contains(key) && !rootObj.has(key)) {
                    rootObj.set(key, entry.getValue());
                    fields.remove();
                    changed = true;
                }
            }
            return changed ? OBJECT_MAPPER.writeValueAsString(rootObj) : queryJson;
        } catch (Exception ignored) {
            return queryJson;
        }
    }

    static String wrapQueryBodyIfNeeded(String queryJson) {
        if (queryJson == null || queryJson.isBlank()) {
            return queryJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(queryJson);
            if (!root.isObject() || root.has("query")) {
                return queryJson;
            }
            if (looksLikeElasticsearchQueryBody(root)) {
                ObjectNode wrapped = OBJECT_MAPPER.createObjectNode();
                wrapped.set("query", root);
                return OBJECT_MAPPER.writeValueAsString(wrapped);
            }
            return queryJson;
        } catch (Exception ignored) {
            return queryJson;
        }
    }

    private static boolean looksLikeElasticsearchQueryBody(JsonNode node) {
        return node.has("bool")
                || node.has("match_all")
                || node.has("term")
                || node.has("terms")
                || node.has("match")
                || node.has("range")
                || node.has("filter")
                || node.has("must")
                || node.has("should");
    }

    private static String bareToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        return toolName.contains(".") ? toolName.substring(toolName.indexOf('.') + 1) : toolName;
    }
}
