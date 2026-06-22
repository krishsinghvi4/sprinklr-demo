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
import java.util.Optional;

/**
 * Blocks {@code createJiraIssue} when metadata was not fetched for the target project + issue type,
 * when required fields are missing from the create payload, or when display names are used as keys.
 */
@Component
public class JiraCreateIssuePreflightGuard implements McpInvocationPreflightStrategy {

    private static final Logger log = LoggerFactory.getLogger(JiraCreateIssuePreflightGuard.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JiraIssueTypeCreateRequirementsCache requirementsCache;

    public JiraCreateIssuePreflightGuard(JiraIssueTypeCreateRequirementsCache requirementsCache) {
        this.requirementsCache = requirementsCache;
    }

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        return validate(invocation.serverId(), invocation.toolName(), invocation.argumentsJson());
    }

    PreflightResult validate(String connectionId, String toolName, String argumentsJson) {
        if (!isCreateJiraIssue(toolName)) {
            return PreflightResult.allow();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);

            Optional<JiraIssueTypeMetadataProcessor.CreateScope> scope =
                    JiraIssueTypeMetadataProcessor.parseCreateScope(argumentsJson);
            if (scope.isEmpty()) {
                return PreflightResult.block(
                        "createJiraIssue is missing projectKey and issueTypeName. "
                                + "Call getJiraIssueTypeMetaWithFields for the target project and issue type first."
                );
            }

            JiraIssueTypeMetadataProcessor.CreateScope createScope = scope.get();
            Optional<List<JiraIssueTypeMetadataProcessor.RequiredField>> cached = requirementsCache.resolve(
                    connectionId,
                    createScope.projectKey(),
                    createScope.issueTypeLookupKeys()
            );
            if (cached.isEmpty()) {
                return PreflightResult.block(
                        "Call getJiraIssueTypeMetaWithFields for project "
                                + createScope.projectKey()
                                + " and issue type "
                                + String.join("/", createScope.issueTypeLookupKeys())
                                + " before calling createJiraIssue."
                );
            }

            List<String> violations = new ArrayList<>();
            collectDisplayNameAdditionalFieldKeys(root.path("additional_fields"), violations);
            collectMissingRequiredFields(root, cached.get(), violations);

            if (violations.isEmpty()) {
                return PreflightResult.allow();
            }

            return PreflightResult.block(String.join(" ", violations));
        } catch (Exception exception) {
            log.warn("[JiraPreflight] Failed to validate createJiraIssue arguments: {}", exception.getMessage());
            return PreflightResult.block(
                    "Could not validate createJiraIssue arguments. Call getJiraIssueTypeMetaWithFields "
                            + "and ensure every required field is filled before calling createJiraIssue."
            );
        }
    }

    private void collectMissingRequiredFields(
            JsonNode root,
            List<JiraIssueTypeMetadataProcessor.RequiredField> requiredFields,
            List<String> violations
    ) {
        List<String> missing = new ArrayList<>();
        for (JiraIssueTypeMetadataProcessor.RequiredField field : requiredFields) {
            if (!isRequiredFieldPresent(root, field)) {
                missing.add(field.name());
            }
        }
        if (!missing.isEmpty()) {
            violations.add(
                    "Missing required fields: " + String.join(", ", missing) + ". "
                            + "Do NOT call createJiraIssue. Reply to the user, list allowed options from "
                            + "getJiraIssueTypeMetaWithFields metadata, and wait for their choice."
            );
        }
    }

    private boolean isRequiredFieldPresent(JsonNode root, JiraIssueTypeMetadataProcessor.RequiredField field) {
        if ("top_level".equals(field.placement())) {
            return hasNonEmptyValue(root.path(field.fieldId()));
        }
        if ("additional_fields".equals(field.placement())) {
            JsonNode additionalFields = root.path("additional_fields");
            if (!additionalFields.isObject()) {
                return false;
            }
            String key = field.additionalFieldsKey() != null && !field.additionalFieldsKey().isBlank()
                    ? field.additionalFieldsKey()
                    : field.fieldId();
            return hasNonEmptyValue(additionalFields.path(key));
        }
        return true;
    }

    private boolean hasNonEmptyValue(JsonNode valueNode) {
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return false;
        }
        if (valueNode.isTextual()) {
            return !valueNode.asText().isBlank();
        }
        if (valueNode.isArray()) {
            return valueNode.size() > 0;
        }
        if (valueNode.isObject()) {
            if (valueNode.has("value")) {
                JsonNode value = valueNode.get("value");
                if (value.isTextual()) {
                    return !value.asText().isBlank();
                }
                return !value.isNull() && !value.isMissingNode();
            }
            if (valueNode.has("name")) {
                return !valueNode.path("name").asText("").isBlank();
            }
            return valueNode.size() > 0;
        }
        return true;
    }

    private void collectDisplayNameAdditionalFieldKeys(JsonNode additionalFields, List<String> violations) {
        if (!additionalFields.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = additionalFields.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if (key.contains(" ")) {
                violations.add(
                        "additional_fields key \"" + key + "\" uses a display name — use additionalFieldsKey "
                                + "from getJiraIssueTypeMetaWithFields metadata (customfield_* id)."
                );
            }
        }
    }

    private static boolean isCreateJiraIssue(String toolName) {
        if (toolName == null) {
            return false;
        }
        String bare = toolName.contains(".") ? toolName.substring(toolName.indexOf('.') + 1) : toolName;
        return "createJiraIssue".equals(bare);
    }
}
