package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpInvocationContext;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpToolResultPostProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Paginates and summarizes Jira issue-type field metadata for create/edit flows.
 */
@Component
public class JiraIssueTypeMetadataPostProcessor implements McpToolResultPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueTypeMetadataPostProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean supports(McpCatalogEntry entry, String toolName) {
        return "jira".equals(entry.serverIdPrefix())
                && "getJiraIssueTypeMetaWithFields".equals(bareToolName(toolName));
    }

    @Override
    public String process(
            McpCatalogEntry entry,
            String toolName,
            String rawContent,
            McpInvocationContext context
    ) {
        try {
            JsonNode firstPage = OBJECT_MAPPER.readTree(rawContent);
            List<JsonNode> additionalPages = new ArrayList<>();
            int fetchedCount = JiraIssueTypeMetadataProcessor.analyzeSinglePage(firstPage).fetchedFieldCount();
            int startAt = JiraIssueTypeMetadataProcessor.nextStartAt(firstPage, fetchedCount);
            JsonNode latestPage = firstPage;
            int pageGuard = 0;

            while (JiraIssueTypeMetadataProcessor.needsMorePages(latestPage, fetchedCount) && pageGuard < 25) {
                pageGuard++;
                String pagedArgs = withMetadataPagination(context.argumentsJson(), startAt);
                log.info("[JiraMetadata] Fetching paginated field metadata connectionId={} startAt={}",
                        context.connection().id(), startAt);
                String nextContent = context.callToolAndFormat().apply(toolName, pagedArgs);
                if (nextContent == null || nextContent.isBlank()) {
                    break;
                }
                JsonNode nextPage = OBJECT_MAPPER.readTree(nextContent);
                additionalPages.add(nextPage);
                fetchedCount += JiraIssueTypeMetadataProcessor.analyzeSinglePage(nextPage).fetchedFieldCount();
                latestPage = nextPage;
                startAt = JiraIssueTypeMetadataProcessor.nextStartAt(nextPage, fetchedCount);
            }

            JiraIssueTypeMetadataProcessor.ProcessedMetadata processed = additionalPages.isEmpty()
                    ? JiraIssueTypeMetadataProcessor.analyzeSinglePage(firstPage)
                    : JiraIssueTypeMetadataProcessor.mergePages(firstPage, additionalPages);

            Optional<JiraIssueTypeMetadataProcessor.CreateScope> scope =
                    JiraIssueTypeMetadataProcessor.resolveCreateScope(context.argumentsJson(), processed);

            return JiraIssueTypeMetadataProcessor.summarize(processed, scope).orElse(rawContent);
        } catch (Exception exception) {
            log.warn("[JiraMetadata] Failed to process metadata for connectionId={}: {}",
                    context.connection().id(), exception.getMessage());
            return rawContent;
        }
    }

    private String withMetadataPagination(String argumentsJson, int startAt) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
        if (!root.isObject()) {
            ObjectNode args = OBJECT_MAPPER.createObjectNode();
            args.put("startAt", startAt);
            return OBJECT_MAPPER.writeValueAsString(args);
        }
        ObjectNode args = (ObjectNode) root;
        args.put("startAt", startAt);
        if (!args.has("maxResults")) {
            args.put("maxResults", 200);
        }
        return OBJECT_MAPPER.writeValueAsString(args);
    }

    private static String bareToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        return toolName.contains(".") ? toolName.substring(toolName.indexOf('.') + 1) : toolName;
    }
}
