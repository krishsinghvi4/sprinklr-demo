package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Repairs common LLM mistakes when calling Atlassian hosted MCP Jira write tools.
 */
@Component
public class AtlassianJiraToolArgumentNormalizer {

    private static final Logger log = LoggerFactory.getLogger(AtlassianJiraToolArgumentNormalizer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> JIRA_WRITE_TOOLS = Set.of("createJiraIssue", "editJiraIssue");

    private static final Set<String> ADDITIONAL_FIELDS_ONLY_KEYS = Set.of(
            "components",
            "priority",
            "labels"
    );

    private final JiraIssueTypeFieldShapeCache fieldShapeCache;

    public AtlassianJiraToolArgumentNormalizer(JiraIssueTypeFieldShapeCache fieldShapeCache) {
        this.fieldShapeCache = fieldShapeCache;
    }

    public boolean supports(String toolName) {
        return bareToolName(toolName) != null && JIRA_WRITE_TOOLS.contains(bareToolName(toolName));
    }

    public String normalize(String toolName, String argumentsJson) {
        return normalize(toolName, argumentsJson, null);
    }

    public String normalize(String toolName, String argumentsJson, String connectionId) {
        if (!supports(toolName) || argumentsJson == null || argumentsJson.isBlank()) {
            return argumentsJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            if (!root.isObject()) {
                return argumentsJson;
            }
            ObjectNode normalized = normalizeWriteArguments((ObjectNode) root, connectionId);
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

    private ObjectNode normalizeWriteArguments(ObjectNode root, String connectionId) {
        ObjectNode additionalFields = ensureAdditionalFieldsObject(root);
        nestFieldsIntoAdditionalFields(root, additionalFields);
        normalizeComponentsInAdditionalFields(additionalFields);
        normalizeCustomFieldValues(additionalFields);
        normalizeFieldValueShapes(additionalFields, connectionId);
        removeUnsupportedCreateFields(additionalFields);
        if (additionalFields.isEmpty()) {
            root.remove("additional_fields");
        } else {
            root.set("additional_fields", additionalFields);
        }
        return root;
    }

    private void normalizeFieldValueShapes(ObjectNode additionalFields, String connectionId) {
        Iterator<Map.Entry<String, JsonNode>> iterator = additionalFields.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            if ("components".equals(key) || "labels".equals(key) || "priority".equals(key)) {
                continue;
            }
            String shape = fieldShapeCache.valueShape(connectionId, key);
            JsonNode value = entry.getValue();
            if ("option_array".equals(shape) || "version_array".equals(shape)) {
                additionalFields.set(key, ensureArrayValue(value));
                continue;
            }
            if ("option_object".equals(shape) && value.isArray() && value.size() == 1 && value.get(0).isObject()) {
                additionalFields.set(key, value.get(0).deepCopy());
            }
        }
    }

    private ArrayNode ensureArrayValue(JsonNode value) {
        if (value.isArray()) {
            return (ArrayNode) value.deepCopy();
        }
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        if (value.isObject()) {
            array.add(value.deepCopy());
        } else if (value.isTextual()) {
            ObjectNode wrapped = OBJECT_MAPPER.createObjectNode();
            wrapped.put("value", value.asText());
            array.add(wrapped);
        }
        return array;
    }

    private ObjectNode ensureAdditionalFieldsObject(ObjectNode root) {
        ObjectNode extracted = extractAdditionalFieldsObject(root);
        if (extracted != null) {
            return extracted;
        }
        ObjectNode created = OBJECT_MAPPER.createObjectNode();
        root.set("additional_fields", created);
        return created;
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

    private void nestFieldsIntoAdditionalFields(ObjectNode root, ObjectNode additionalFields) {
        for (String key : ADDITIONAL_FIELDS_ONLY_KEYS) {
            JsonNode value = root.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (!additionalFields.has(key) || additionalFields.get(key).isNull()) {
                if ("components".equals(key)) {
                    additionalFields.set("components", toComponentsArray(value));
                } else {
                    additionalFields.set(key, value.deepCopy());
                }
            }
            root.remove(key);
        }
    }

    private void normalizeComponentsInAdditionalFields(ObjectNode additionalFields) {
        JsonNode components = additionalFields.get("components");
        if (components != null && !components.isNull()) {
            additionalFields.set("components", toComponentsArray(components));
        }
    }

    private ArrayNode toComponentsArray(JsonNode componentsNode) {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        if (componentsNode.isArray()) {
            for (JsonNode item : componentsNode) {
                appendComponentEntry(array, item);
            }
            return array;
        }
        if (componentsNode.isTextual()) {
            for (String part : componentsNode.asText().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    array.add(componentByName(trimmed));
                }
            }
            return array;
        }
        appendComponentEntry(array, componentsNode);
        return array;
    }

    private void appendComponentEntry(ArrayNode array, JsonNode item) {
        if (item.isObject()) {
            if (item.has("id")) {
                ObjectNode byId = OBJECT_MAPPER.createObjectNode();
                byId.set("id", item.get("id"));
                array.add(byId);
                return;
            }
            if (item.has("name")) {
                array.add(componentByName(item.get("name").asText()));
            }
            return;
        }
        if (item.isTextual()) {
            array.add(componentByName(item.asText()));
        }
    }

    private ObjectNode componentByName(String name) {
        ObjectNode component = OBJECT_MAPPER.createObjectNode();
        component.put("name", name);
        return component;
    }

    private void removeUnsupportedCreateFields(ObjectNode additionalFields) {
        additionalFields.remove("issuelinks");
        additionalFields.remove("Linked Issues");
    }

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

    private static String bareToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        return toolName.contains(".") ? toolName.substring(toolName.indexOf('.') + 1) : toolName;
    }
}
