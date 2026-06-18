package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Repairs common LLM mistakes when calling Atlassian hosted MCP Jira write tools.
 * <p>
 * The model often places {@code components} inside {@code additional_fields} or sends plain
 * strings for select-list custom fields. This normalizer hoists known top-level fields and
 * wraps custom-field scalar values in {@code {"value": "..."}} when appropriate.
 */
@Component
public class AtlassianJiraToolArgumentNormalizer {

    private static final Logger log = LoggerFactory.getLogger(AtlassianJiraToolArgumentNormalizer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> JIRA_WRITE_TOOLS = Set.of("createJiraIssue", "editJiraIssue");

    /** Fields with dedicated top-level parameters on createJiraIssue — must not live in additional_fields. */
    private static final Set<String> TOP_LEVEL_FIELD_KEYS = Set.of(
            "components",
            "assignee",
            "assignee_account_id",
            "priority",
            "labels",
            "parent",
            "description",
            "summary"
    );

    public boolean supports(String toolName) {
        return toolName != null && JIRA_WRITE_TOOLS.contains(toolName);
    }

    /**
     * @return normalized JSON arguments, or the original string when parsing/normalization is not possible
     */
    public String normalize(String toolName, String argumentsJson) {
        if (!supports(toolName) || argumentsJson == null || argumentsJson.isBlank()) {
            return argumentsJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            if (!root.isObject()) {
                return argumentsJson;
            }
            ObjectNode normalized = normalizeWriteArguments((ObjectNode) root);
            String result = OBJECT_MAPPER.writeValueAsString(normalized);
            if (!result.equals(argumentsJson)) {
                log.info("[MCP] Normalized Atlassian Jira tool={} args before={} after={}",
                        toolName, argumentsJson, result);
            }
            return result;
        } catch (Exception exception) {
            log.warn("[MCP] Failed to normalize Atlassian Jira args for tool={}: {}",
                    toolName, exception.getMessage());
            return argumentsJson;
        }
    }

    private ObjectNode normalizeWriteArguments(ObjectNode root) {
        ObjectNode additionalFields = extractAdditionalFieldsObject(root);
        if (additionalFields != null) {
            hoistTopLevelFields(root, additionalFields);
            normalizeCustomFieldValues(additionalFields);
            if (additionalFields.isEmpty()) {
                root.remove("additional_fields");
            } else {
                root.set("additional_fields", additionalFields);
            }
        }
        normalizeTopLevelComponents(root);
        return root;
    }

    private ObjectNode extractAdditionalFieldsObject(ObjectNode root) {
        JsonNode additionalFieldsNode = root.get("additional_fields");
        if (additionalFieldsNode == null || additionalFieldsNode.isNull()) {
            return null;
        }
        if (additionalFieldsNode.isObject()) {
            return (ObjectNode) additionalFieldsNode.deepCopy();
        }
        if (additionalFieldsNode.isTextual()) {
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(additionalFieldsNode.asText());
                if (parsed.isObject()) {
                    return (ObjectNode) parsed.deepCopy();
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private void hoistTopLevelFields(ObjectNode root, ObjectNode additionalFields) {
        Iterator<Map.Entry<String, JsonNode>> iterator = additionalFields.fields();
        Map<String, JsonNode> toHoist = new LinkedHashMap<>();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (TOP_LEVEL_FIELD_KEYS.contains(entry.getKey())) {
                toHoist.put(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
        for (Map.Entry<String, JsonNode> entry : toHoist.entrySet()) {
            if (!root.has(entry.getKey()) || root.get(entry.getKey()).isNull()) {
                if ("components".equals(entry.getKey())) {
                    root.set("components", toComponentsArray(entry.getValue()));
                } else {
                    root.set(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void normalizeTopLevelComponents(ObjectNode root) {
        JsonNode components = root.get("components");
        if (components != null && !components.isNull()) {
            root.set("components", toComponentsArray(components));
        }
    }

    private ArrayNode toComponentsArray(JsonNode componentsNode) {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        if (componentsNode.isArray()) {
            for (JsonNode item : componentsNode) {
                appendComponentName(array, item);
            }
            return array;
        }
        if (componentsNode.isTextual()) {
            for (String part : componentsNode.asText().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    array.add(trimmed);
                }
            }
            return array;
        }
        appendComponentName(array, componentsNode);
        return array;
    }

    private void appendComponentName(ArrayNode array, JsonNode item) {
        if (item.isObject() && item.has("name")) {
            array.add(item.get("name").asText());
            return;
        }
        if (item.isTextual()) {
            array.add(item.asText());
        }
    }

    /**
     * Jira select-list custom fields reject bare strings; they expect {@code {"value": "..."}}
     * or {@code {"name": "..."}} depending on field type.
     */
    private void normalizeCustomFieldValues(ObjectNode additionalFields) {
        Iterator<Map.Entry<String, JsonNode>> iterator = additionalFields.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!key.startsWith("customfield_") || !value.isTextual()) {
                continue;
            }
            ObjectNode wrapped = OBJECT_MAPPER.createObjectNode();
            wrapped.put("value", value.asText());
            additionalFields.set(key, wrapped);
        }
    }
}
