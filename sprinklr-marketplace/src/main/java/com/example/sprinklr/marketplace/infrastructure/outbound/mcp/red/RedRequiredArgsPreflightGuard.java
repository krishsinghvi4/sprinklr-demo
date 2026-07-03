package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight.McpInvocationPreflightStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Lightweight RED audit-log guard: enforces Jira fetch ordering for the dedicated audit tool and
 * validates {@code env} against a closed allowlist. Value provenance for partnerId/assetId is left
 * to skills and MCP server validation — preflight tool-result text is truncated and unreliable for
 * Jira tickets with IDs buried in comments.
 */
@Component
public class RedRequiredArgsPreflightGuard implements McpInvocationPreflightStrategy {

    private static final Logger log = LoggerFactory.getLogger(RedRequiredArgsPreflightGuard.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RED_PREFIX = "red";
    private static final String AUDIT_LOG_SERVER_TYPE = "AUDIT_LOG";
    private static final String AUDIT_LOG_EXECUTE_TOOL = "red_execute_audit_log_elasticsearch_query";
    private static final String JIRA_GET_ISSUE = "jira.getJiraIssue";
    private static final String JIRA_GET_ISSUE_BARE = "getJiraIssue";
    private static final String TOOLS_CALLED_MARKER = "[tools-called-this-turn:";

    private static final Set<String> GENERIC_AUDIT_ES_TOOLS = Set.of(
            "red_sample_elasticsearch_query",
            "red_execute_elastic_search_query"
    );

    private final McpConnectionRepository connectionRepository;
    private final RedEnvironmentAllowlist environmentAllowlist;

    public RedRequiredArgsPreflightGuard(
            McpConnectionRepository connectionRepository,
            RedEnvironmentAllowlist environmentAllowlist
    ) {
        this.connectionRepository = connectionRepository;
        this.environmentAllowlist = environmentAllowlist;
    }

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        return validate(
                invocation.toolName(),
                invocation.argumentsJson(),
                userPrompt,
                invocation.serverId()
        );
    }

    PreflightResult validate(String toolName, String argumentsJson, String userPrompt, String connectionId) {
        if (!isRedAuditTool(toolName, connectionId)) {
            return PreflightResult.allow();
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return PreflightResult.allow();
        }

        String bareToolName = bareToolName(toolName);

        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            if (AUDIT_LOG_EXECUTE_TOOL.equals(bareToolName)) {
                return validateDedicatedAuditLogTool(root, userPrompt, bareToolName);
            }
            return validateGenericAuditEsTool(root, userPrompt, bareToolName);
        } catch (Exception exception) {
            log.warn("[RedPreflight] Failed to validate {} arguments: {}", bareToolName, exception.getMessage());
            return PreflightResult.block(
                    "Could not validate RED audit log tool arguments before calling " + bareToolName + "."
            );
        }
    }

    private PreflightResult validateDedicatedAuditLogTool(JsonNode root, String userPrompt, String bareToolName) {
        if (!jiraGetIssueCalledThisTurn(userPrompt)) {
            return PreflightResult.block(
                    "Fetch the Jira ticket with getJiraIssue first in this turn before calling " + bareToolName + "."
            );
        }

        JsonNode envNode = root.path("env");
        if (envNode.isMissingNode() || envNode.isNull() || envNode.asText("").isBlank()) {
            return PreflightResult.block(
                    "env is required for " + bareToolName + ". Extract the exact env token from the Jira ticket."
            );
        }
        PreflightResult envResult = validateEnvAllowlist(envNode, bareToolName);
        if (!envResult.allowed()) {
            return envResult;
        }

        JsonNode queryNode = root.path("query");
        if (queryNode.isMissingNode() || queryNode.isNull()
                || (queryNode.isTextual() && queryNode.asText("").isBlank())) {
            return PreflightResult.block(
                    "query is required for " + bareToolName + ". Build a filter on assetId from the Jira ticket."
            );
        }

        return PreflightResult.allow();
    }

    private PreflightResult validateGenericAuditEsTool(JsonNode root, String userPrompt, String bareToolName) {
        if (!isAuditLogServerType(root.path("serverType"))) {
            return PreflightResult.allow();
        }

        JsonNode indexNameNode = root.path("indexName");
        if (indexNameNode.isMissingNode() || indexNameNode.isNull() || indexNameNode.asText("").isBlank()) {
            return PreflightResult.block(
                    "indexName is required for AUDIT_LOG queries (e.g. audit_log_p{partnerId}*). "
                            + "Do NOT call " + bareToolName + " without it."
            );
        }

        JsonNode envNode = root.path("env");
        if (!envNode.isMissingNode() && !envNode.isNull()) {
            String env = envNode.asText("").trim();
            if (!env.isBlank()) {
                PreflightResult envResult = validateEnvAllowlist(envNode, bareToolName);
                if (!envResult.allowed()) {
                    return envResult;
                }
            }
        }

        return PreflightResult.allow();
    }

    private PreflightResult validateEnvAllowlist(JsonNode envNode, String bareToolName) {
        String env = envNode.asText("").trim();
        if (!environmentAllowlist.isAllowed(env)) {
            return PreflightResult.block(
                    "env \"" + env + "\" is not a valid RED environment. env must be one of: "
                            + environmentAllowlist.allowedValuesDescription()
            );
        }
        return PreflightResult.allow();
    }

    static boolean jiraGetIssueCalledThisTurn(String context) {
        String toolsList = extractToolsCalledMarker(context);
        if (toolsList.isEmpty()) {
            return false;
        }
        for (String called : toolsList.split(",")) {
            String trimmed = called.trim();
            if (JIRA_GET_ISSUE.equals(trimmed) || JIRA_GET_ISSUE_BARE.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private static String extractToolsCalledMarker(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        int markerStart = context.indexOf(TOOLS_CALLED_MARKER);
        if (markerStart < 0) {
            return "";
        }
        int markerEnd = context.indexOf(']', markerStart);
        if (markerEnd < 0) {
            return "";
        }
        return context.substring(markerStart + TOOLS_CALLED_MARKER.length(), markerEnd).trim();
    }

    private static boolean isAuditLogServerType(JsonNode serverTypeNode) {
        if (serverTypeNode.isMissingNode() || serverTypeNode.isNull()) {
            return false;
        }
        return AUDIT_LOG_SERVER_TYPE.equalsIgnoreCase(serverTypeNode.asText("").trim());
    }

    private boolean isRedAuditTool(String toolName, String connectionId) {
        if (toolName == null) {
            return false;
        }
        String bare = bareToolName(toolName);
        if (!GENERIC_AUDIT_ES_TOOLS.contains(bare) && !AUDIT_LOG_EXECUTE_TOOL.equals(bare)) {
            return false;
        }
        if (toolName.startsWith(RED_PREFIX + ".")) {
            return true;
        }
        return connectionRepository.findById(connectionId)
                .map(connection -> RED_PREFIX.equals(connection.serverIdPrefix()))
                .orElse(false);
    }

    private static String bareToolName(String toolName) {
        int separator = toolName.indexOf('.');
        return separator > 0 ? toolName.substring(separator + 1) : toolName;
    }
}
