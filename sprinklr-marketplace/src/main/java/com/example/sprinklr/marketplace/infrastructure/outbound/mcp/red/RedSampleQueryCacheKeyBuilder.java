package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

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
        String id = hash(userId, connectionId, sampleToolName, scopeArgsJson);
        return new RedSampleQueryCacheKey(id, scopeArgsJson);
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

    private static String hash(String userId, String connectionId, String toolName, String scopeArgsJson) {
        String material = userId + "|" + connectionId + "|" + toolName + "|" + scopeArgsJson;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
