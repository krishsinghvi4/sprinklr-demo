package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Injects server-side sample limits and default recency sort into RED sample tool arguments.
 */
public final class RedSampleQueryArgsEnricher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RedSampleQueryArgsEnricher() {
    }

    public static String enrich(String bareToolName, String argumentsJson, int schemaDiscoveryLimit) {
        if (schemaDiscoveryLimit <= 0) {
            return argumentsJson;
        }
        try {
            ObjectNode args = (ObjectNode) OBJECT_MAPPER.readTree(
                    argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            if (RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL.equals(bareToolName)) {
                enrichMongo(args, schemaDiscoveryLimit);
            }
            return OBJECT_MAPPER.writeValueAsString(args);
        } catch (Exception exception) {
            return argumentsJson;
        }
    }

    private static void enrichMongo(ObjectNode args, int schemaDiscoveryLimit) {
        args.put("limit", schemaDiscoveryLimit);
        if (!hasNonBlankText(args, "sortField")) {
            args.put("sortField", "createdTime");
        }
        if (!hasNonBlankText(args, "sortDirection")) {
            args.put("sortDirection", "desc");
        }
    }

    private static boolean hasNonBlankText(ObjectNode args, String field) {
        JsonNode value = args.path(field);
        return value.isTextual() && !value.asText("").isBlank();
    }
}
