package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

/**
 * Builds stable cache keys from RED sample (or execute) scope arguments.
 */
@Component
public class RedSampleQueryCacheKeyBuilder {

    public static final String ES_SAMPLE_TOOL = "red_sample_elasticsearch_query";
    public static final String MONGO_SAMPLE_TOOL = "red_sample_mongo_query";
    public static final String ES_EXECUTE_TOOL = "red_execute_elastic_search_query";
    public static final String MONGO_EXECUTE_TOOL = "red_execute_mongo_query";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpProperties mcpProperties;

    public RedSampleQueryCacheKeyBuilder(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    private static final List<String> ES_SCOPE_FIELDS = List.of(
            "partnerId",
            "serverType",
            "searchType",
            "indexName",
            "username",
            "env",
            "preference",
            "host",
            "queryExecutorLongQuery"
    );

    private static final List<String> MONGO_SCOPE_FIELDS = List.of(
            "partnerId",
            "collectionName",
            "queryType",
            "serverType",
            "sequenceName",
            "skip",
            "includeFields",
            "sortField",
            "sortDirection",
            "env"
    );

    private static final List<String> ES_REQUIRED_SCOPE_FIELDS = List.of(
            "partnerId",
            "serverType",
            "searchType",
            "indexName"
    );

    private static final List<String> MONGO_REQUIRED_SCOPE_FIELDS = List.of(
            "partnerId",
            "collectionName",
            "queryType",
            "serverType"
    );

    public boolean isSampleTool(String bareToolName) {
        return ES_SAMPLE_TOOL.equals(bareToolName) || MONGO_SAMPLE_TOOL.equals(bareToolName);
    }

    public boolean isExecuteTool(String bareToolName) {
        return ES_EXECUTE_TOOL.equals(bareToolName) || MONGO_EXECUTE_TOOL.equals(bareToolName);
    }

    public String sampleToolForExecute(String bareExecuteToolName) {
        if (ES_EXECUTE_TOOL.equals(bareExecuteToolName)) {
            return ES_SAMPLE_TOOL;
        }
        if (MONGO_EXECUTE_TOOL.equals(bareExecuteToolName)) {
            return MONGO_SAMPLE_TOOL;
        }
        return null;
    }

    public String fullyQualifiedSampleTool(String serverPrefix, String bareSampleToolName) {
        return serverPrefix + "." + bareSampleToolName;
    }

    public RedSampleQueryCacheKey build(String userId, String connectionId, String bareToolName, String argumentsJson) {
        String sampleToolName = resolveSampleToolName(bareToolName);
        if (sampleToolName == null) {
            throw new IllegalArgumentException("Unsupported RED tool for sample cache: " + bareToolName);
        }
        String scopeArgsJson = canonicalScopeArgsJson(sampleToolName, argumentsJson);
        String id = hash(cacheVersion(), userId, connectionId, sampleToolName, scopeArgsJson);
        return new RedSampleQueryCacheKey(id, scopeArgsJson);
    }

    private String cacheVersion() {
        if (mcpProperties.getRed() == null || mcpProperties.getRed().getSampleQueryCache() == null) {
            return "1";
        }
        String version = mcpProperties.getRed().getSampleQueryCache().getVersion();
        return version == null || version.isBlank() ? "1" : version;
    }

    /**
     * Returns true when the canonical scope includes all fields required to distinguish cache entries.
     * Incomplete scopes (e.g. missing {@code collectionName}) must not be cached or served from cache.
     */
    public boolean isCompleteScopeForCache(String sampleToolName, String scopeArgsJson) {
        if (sampleToolName == null || scopeArgsJson == null || scopeArgsJson.isBlank()) {
            return false;
        }
        try {
            JsonNode scope = OBJECT_MAPPER.readTree(scopeArgsJson);
            List<String> required = ES_SAMPLE_TOOL.equals(sampleToolName)
                    ? ES_REQUIRED_SCOPE_FIELDS
                    : MONGO_REQUIRED_SCOPE_FIELDS;
            for (String field : required) {
                if (!hasNonBlankScopeValue(scope, field)) {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean hasNonBlankScopeValue(JsonNode scope, String field) {
        JsonNode value = scope.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText("").isBlank();
        }
        return true;
    }

    private String resolveSampleToolName(String bareToolName) {
        if (ES_SAMPLE_TOOL.equals(bareToolName) || MONGO_SAMPLE_TOOL.equals(bareToolName)) {
            return bareToolName;
        }
        return sampleToolForExecute(bareToolName);
    }

    String canonicalScopeArgsJson(String sampleToolName, String argumentsJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            List<String> fields = ES_SAMPLE_TOOL.equals(sampleToolName) ? ES_SCOPE_FIELDS : MONGO_SCOPE_FIELDS;
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            for (String field : fields) {
                JsonNode value = root.path(field);
                if (value.isMissingNode() || value.isNull()) {
                    continue;
                }
                if (value.isTextual() && value.asText("").isBlank()) {
                    continue;
                }
                sorted.put(field, value);
            }
            if (ES_SAMPLE_TOOL.equals(sampleToolName) && !sorted.containsKey("username")) {
                sorted.put("username", OBJECT_MAPPER.getNodeFactory().textNode("test"));
            }
            ObjectNode canonical = OBJECT_MAPPER.createObjectNode();
            sorted.forEach(canonical::set);
            return OBJECT_MAPPER.writeValueAsString(canonical);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RED tool arguments for cache key: " + exception.getMessage());
        }
    }

    private static String hash(
            String cacheVersion,
            String userId,
            String connectionId,
            String toolName,
            String scopeArgsJson
    ) {
        String material = cacheVersion + "|" + userId + "|" + connectionId + "|" + toolName + "|" + scopeArgsJson;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
