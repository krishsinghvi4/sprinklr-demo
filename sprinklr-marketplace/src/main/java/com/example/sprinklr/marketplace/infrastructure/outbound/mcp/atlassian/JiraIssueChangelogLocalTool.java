package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolExtension;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolInvocationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local Jira tool that fetches issue change history via the official Atlassian MCP {@code getJiraIssue} tool.
 * Direct Jira REST calls are not used because OAuth tokens issued for MCP are scoped to the MCP resource,
 * not {@code api.atlassian.com}.
 */
@Component
public class JiraIssueChangelogLocalTool implements McpLocalToolExtension {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueChangelogLocalTool.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TOOL_NAME = "getJiraIssueChangelog";
    private static final String MCP_GET_ISSUE_TOOL = "getJiraIssue";
    private static final int DEFAULT_MAX_RESULTS = 100;

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "cloudId": {
                  "type": "string",
                  "description": "Atlassian cloud ID from getAccessibleAtlassianResources"
                },
                "issueKey": {
                  "type": "string",
                  "description": "Jira issue key, e.g. ITOPS-123"
                },
                "maxResults": {
                  "type": "integer",
                  "description": "Maximum changelog entries to return (default 100)"
                }
              },
              "required": ["cloudId", "issueKey"]
            }
            """;

    private final StreamableHttpMcpClient mcpClient;

    public JiraIssueChangelogLocalTool(StreamableHttpMcpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return "jira".equals(entry.serverIdPrefix());
    }

    @Override
    public List<McpTool> toolDefinitions(McpCatalogEntry entry, String connectionId) {
        if (!supports(entry)) {
            return List.of();
        }
        return List.of(new McpTool(
                entry.serverIdPrefix() + "." + TOOL_NAME,
                "Returns the full change history for a Jira issue, including status, assignee, priority, "
                        + "and other field changes with timestamps and authors.",
                connectionId,
                INPUT_SCHEMA
        ));
    }

    @Override
    public boolean handles(McpCatalogEntry entry, String bareToolName) {
        return supports(entry) && TOOL_NAME.equals(bareToolName);
    }

    @Override
    public String invoke(McpCatalogEntry entry, String bareToolName, McpLocalToolInvocationContext context) {
        ChangelogArgs args = parseArgs(context.argumentsJson());
        log.info("[JiraLocal] Fetching changelog via MCP connectionId={} issueKey={}",
                context.connection().id(), args.issueKey());

        JsonNode issue = fetchIssueWithChangelog(context, args);
        JsonNode changelog = issue.path("changelog");
        if (changelog.isMissingNode() || changelog.isNull()) {
            throw new McpInvocationException(
                    "Jira issue '" + args.issueKey() + "' has no changelog data",
                    "Missing changelog on MCP getJiraIssue response"
            );
        }

        return JiraChangelogFormatter.summarize(args.issueKey(), limitChangelog(changelog, args.maxResults()));
    }

    private JsonNode fetchIssueWithChangelog(McpLocalToolInvocationContext context, ChangelogArgs args) {
        if (context.session() == null || context.session().sessionId() == null || context.session().sessionId().isBlank()) {
            throw new McpInvocationException(
                    "Jira MCP session is missing — please reconnect Jira from your Profile page",
                    "Missing MCP session for getJiraIssueChangelog"
            );
        }

        ObjectNode mcpArgs = OBJECT_MAPPER.createObjectNode();
        mcpArgs.put("cloudId", args.cloudId());
        mcpArgs.put("issueIdOrKey", args.issueKey());
        mcpArgs.put("expand", "changelog");

        JsonNode result;
        try {
            result = mcpClient.callTool(
                    context.catalogEntry().endpointUrl(),
                    context.authHeaders(),
                    context.session().sessionId(),
                    context.session().protocolVersion(),
                    MCP_GET_ISSUE_TOOL,
                    OBJECT_MAPPER.writeValueAsString(mcpArgs)
            );
        } catch (Exception exception) {
            throw new McpInvocationException(
                    "Failed to fetch Jira issue changelog — please try again",
                    exception.getMessage()
            );
        }

        if (result != null && result.path("isError").asBoolean(false)) {
            String message = extractMcpErrorMessage(result);
            throw new McpInvocationException(
                    userMessageForIssueLookupFailure(args.issueKey(), message),
                    message
            );
        }

        String content = extractMcpTextContent(result);
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(content);
            if (parsed.has("error") && parsed.get("error").asBoolean(false)) {
                String message = parsed.path("message").asText(content);
                throw new McpInvocationException(
                        userMessageForIssueLookupFailure(args.issueKey(), message),
                        message
                );
            }
            return parsed;
        } catch (McpInvocationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new McpInvocationException(
                    "Failed to parse Jira issue changelog response",
                    exception.getMessage()
            );
        }
    }

    private static String userMessageForIssueLookupFailure(String issueKey, String detail) {
        if (detail != null && detail.toLowerCase().contains("permission")) {
            return "You don't have permission to view Jira issue '" + issueKey
                    + "'. Ask a project admin for browse access, or reconnect Jira with an account that can see it.";
        }
        return "Could not load change history for Jira issue '" + issueKey
                + "'. Verify the issue key and that your connected Jira account can browse it.";
    }

    private static String extractMcpTextContent(JsonNode result) {
        JsonNode contentNode = result.path("content");
        if (!contentNode.isArray()) {
            throw new McpInvocationException(
                    "Unexpected Jira MCP response format",
                    "Missing content array in MCP tool result"
            );
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : contentNode) {
            if (item.has("text")) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(item.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new McpInvocationException(
                    "Jira MCP returned an empty issue response",
                    "No text content in MCP tool result"
            );
        }
        return text.toString();
    }

    private static String extractMcpErrorMessage(JsonNode result) {
        JsonNode contentNode = result.path("content");
        if (contentNode.isArray()) {
            for (JsonNode item : contentNode) {
                if (item.has("text")) {
                    return item.path("text").asText("Unknown MCP tool error");
                }
            }
        }
        return "Unknown MCP tool error";
    }

    private static JsonNode limitChangelog(JsonNode changelog, int maxResults) {
        JsonNode histories = changelog.path("histories");
        if (!histories.isArray() || histories.size() <= maxResults) {
            return changelog;
        }
        ObjectNode limited = OBJECT_MAPPER.createObjectNode();
        limited.put("total", changelog.path("total").asInt(histories.size()));
        var trimmed = OBJECT_MAPPER.createArrayNode();
        for (int index = 0; index < maxResults; index++) {
            trimmed.add(histories.get(index));
        }
        limited.set("histories", trimmed);
        return limited;
    }

    private ChangelogArgs parseArgs(String argumentsJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String cloudId = root.path("cloudId").asText(null);
            String issueKey = root.path("issueKey").asText(null);
            if (cloudId == null || cloudId.isBlank()) {
                throw new McpInvocationException("cloudId is required", "Missing cloudId in arguments");
            }
            if (issueKey == null || issueKey.isBlank()) {
                throw new McpInvocationException("issueKey is required", "Missing issueKey in arguments");
            }
            int maxResults = root.has("maxResults") ? root.path("maxResults").asInt(DEFAULT_MAX_RESULTS) : DEFAULT_MAX_RESULTS;
            return new ChangelogArgs(cloudId.trim(), issueKey.trim(), Math.max(1, maxResults));
        } catch (McpInvocationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new McpInvocationException(
                    "Invalid arguments for getJiraIssueChangelog",
                    exception.getMessage()
            );
        }
    }

    private record ChangelogArgs(String cloudId, String issueKey, int maxResults) {
    }
}
