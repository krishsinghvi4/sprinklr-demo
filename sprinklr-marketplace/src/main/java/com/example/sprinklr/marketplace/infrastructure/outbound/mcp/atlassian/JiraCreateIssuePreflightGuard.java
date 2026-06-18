package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
public class JiraCreateIssuePreflightGuard {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path DEBUG_LOG = Path.of(
            "/Users/krish.singhvi/Desktop/sprinklr-demo/.cursor/debug-b7bc7c.log"
    );

    public record ValidationResult(boolean allowed, String blockMessage) {
        public static ValidationResult allow() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult block(String message) {
            return new ValidationResult(false, message);
        }
    }

    public ValidationResult validate(String toolName, String argumentsJson, String userPrompt) {
        if (!isCreateJiraIssue(toolName)) {
            return ValidationResult.allow();
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return ValidationResult.allow();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            JsonNode additionalFields = root.path("additional_fields");
            if (!additionalFields.isObject()) {
                return ValidationResult.allow();
            }

            List<String> unconfirmed = new ArrayList<>();
            collectUnconfirmedComponents(additionalFields, userPrompt, unconfirmed);
            collectUnconfirmedCustomFields(additionalFields, userPrompt, unconfirmed);

            // #region agent log
            debugLog("JiraCreateIssuePreflightGuard.validate", "preflight check", Map.of(
                    "hypothesisId", "H2",
                    "userPromptLen", userPrompt.length(),
                    "unconfirmed", unconfirmed,
                    "allowed", unconfirmed.isEmpty()
            ));
            // #endregion

            if (unconfirmed.isEmpty()) {
                return ValidationResult.allow();
            }

            String fields = String.join(", ", unconfirmed);
            return ValidationResult.block(
                    "The user did not specify " + fields + " in their message. "
                            + "Do NOT call createJiraIssue. Reply to the user, list the allowed options "
                            + "from getJiraIssueTypeMetaWithFields metadata, and wait for their choice."
            );
        } catch (Exception ignored) {
            return ValidationResult.allow();
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

    // #region agent log
    private static void debugLog(String location, String message, Map<String, ?> data) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"sessionId\":\"b7bc7c\",\"timestamp\":").append(System.currentTimeMillis());
            json.append(",\"location\":\"").append(location).append("\"");
            json.append(",\"message\":\"").append(message).append("\"");
            json.append(",\"data\":").append(OBJECT_MAPPER.writeValueAsString(data)).append("}");
            Files.writeString(DEBUG_LOG, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
    // #endregion
}
