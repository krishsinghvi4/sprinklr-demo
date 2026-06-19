package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight.McpInvocationPreflightStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Blocks {@code createJiraIssue} when the model invents required field values the user never specified.
 * Component options come from {@code getJiraIssueTypeMetaWithFields}; the model must ask the user
 * to choose — not pick an example from the metadata response.
 */
@Component
public class JiraCreateIssuePreflightGuard implements McpInvocationPreflightStrategy {

    private static final Logger log = LoggerFactory.getLogger(JiraCreateIssuePreflightGuard.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        return validate(invocation.toolName(), invocation.argumentsJson(), userPrompt);
    }

    PreflightResult validate(String toolName, String argumentsJson, String userPrompt) {
        if (!isCreateJiraIssue(toolName)) {
            return PreflightResult.allow();
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return PreflightResult.allow();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            JsonNode additionalFields = root.path("additional_fields");
            if (!additionalFields.isObject()) {
                return PreflightResult.allow();
            }

            List<String> unconfirmed = new ArrayList<>();
            collectUnconfirmedComponents(additionalFields, userPrompt, unconfirmed);
            collectUnconfirmedCustomFields(additionalFields, userPrompt, unconfirmed);

            if (unconfirmed.isEmpty()) {
                return PreflightResult.allow();
            }

            String fields = String.join(", ", unconfirmed);
            return PreflightResult.block(
                    "The user did not specify " + fields + " in their message. "
                            + "Do NOT call createJiraIssue. Reply to the user, list the allowed options "
                            + "from getJiraIssueTypeMetaWithFields metadata, and wait for their choice."
            );
        } catch (Exception exception) {
            log.warn("[JiraPreflight] Failed to validate createJiraIssue arguments: {}", exception.getMessage());
            return PreflightResult.block(
                    "Could not validate createJiraIssue arguments. Ask the user to confirm every required field "
                            + "before calling createJiraIssue."
            );
        }
    }

    private void collectUnconfirmedComponents(JsonNode additionalFields, String userPrompt, List<String> unconfirmed) {
        JsonNode components = additionalFields.path("components");
        if (!components.isArray()) {
            return;
        }
        for (JsonNode component : components) {
            if (component.has("name")) {
                String name = component.path("name").asText();
                if (!userMentionedValue(userPrompt, name)) {
                    unconfirmed.add("component \"" + name + "\"");
                }
            } else if (component.has("id")) {
                String id = component.path("id").asText();
                if (!userMentionedValue(userPrompt, id)) {
                    unconfirmed.add("component id \"" + id + "\"");
                }
            }
        }
    }

    private void collectUnconfirmedCustomFields(JsonNode additionalFields, String userPrompt, List<String> unconfirmed) {
        Iterator<Map.Entry<String, JsonNode>> fields = additionalFields.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if (!key.startsWith("customfield_")) {
                continue;
            }
            JsonNode valueNode = entry.getValue();
            if (valueNode.isObject() && valueNode.has("value") && valueNode.get("value").isTextual()) {
                String value = valueNode.path("value").asText();
                if (!userMentionedValue(userPrompt, value)) {
                    unconfirmed.add(key + " value \"" + value + "\"");
                }
            }
        }
    }

    private boolean userMentionedValue(String userPrompt, String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalizedPrompt = normalize(userPrompt);
        String normalizedValue = normalize(value);
        if (normalizedPrompt.contains(normalizedValue)) {
            return true;
        }
        String[] tokens = normalizedValue.split("\\s+");
        if (tokens.length <= 1) {
            return false;
        }
        int matched = 0;
        for (String token : tokens) {
            if (token.length() > 2 && normalizedPrompt.contains(token)) {
                matched++;
            }
        }
        return matched >= tokens.length - 1;
    }

    private String normalize(String text) {
        return text.toLowerCase().replace('-', ' ').replace('_', ' ').trim();
    }

    private static boolean isCreateJiraIssue(String toolName) {
        if (toolName == null) {
            return false;
        }
        String bare = toolName.contains(".") ? toolName.substring(toolName.indexOf('.') + 1) : toolName;
        return "createJiraIssue".equals(bare);
    }
}
