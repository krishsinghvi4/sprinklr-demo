package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Detects recoverable RED Elasticsearch DSL errors and builds orchestrator nudges to retry execute.
 */
@Component
public class RedEsQueryRetrySupport {

    public static final int MAX_RETRY_NUDGES_PER_TURN = 2;

    private static final Set<String> ELASTICSEARCH_EXECUTE_TOOLS = Set.of(
            "red_execute_elastic_search_query",
            "red_execute_audit_log_elasticsearch_query"
    );

    public boolean isRedElasticsearchExecuteTool(String toolName) {
        String bare = bareToolName(toolName);
        return bare != null && ELASTICSEARCH_EXECUTE_TOOLS.contains(bare);
    }

    public boolean isRecoverableElasticsearchError(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase();
        return lower.contains("parsing_exception")
                || lower.contains("malformed query")
                || (lower.contains("\"status\":400") && lower.contains("\"error\""));
    }

    /**
     * Returns the execute tool name from the batch that returned a recoverable ES error, if any.
     */
    public Optional<String> findRecoverableExecuteTool(
            List<String> toolNames,
            List<McpInvocationResult> results
    ) {
        int size = Math.min(toolNames.size(), results.size());
        for (int i = size - 1; i >= 0; i--) {
            String toolName = toolNames.get(i);
            McpInvocationResult result = results.get(i);
            if (!isRedElasticsearchExecuteTool(toolName)) {
                continue;
            }
            String content = result.success() ? result.content() : result.errorMessage();
            if (isRecoverableElasticsearchError(content)) {
                return Optional.of(toolName);
            }
            if (result.success() && content != null && !content.isBlank()
                    && !isRecoverableElasticsearchError(content)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public String buildRetryNudge(String existingContext, String executeToolName) {
        String bareName = bareToolName(executeToolName);
        String nudge = "RED Elasticsearch query failed with a recoverable DSL error. Do NOT show the raw API error "
                + "as your final answer. Fix the search body and call " + bareName + " again in this turn. "
                + "Critical: sort, size, from, _source, and aggs must be top-level siblings of query — "
                + "never nest them inside the query object.";
        if (existingContext == null || existingContext.isBlank()) {
            return nudge;
        }
        return existingContext + "\n\n" + nudge;
    }

    public String appendRecoverableErrorHint(String rawContent) {
        if (!isRecoverableElasticsearchError(rawContent)) {
            return rawContent;
        }
        return rawContent
                + "\n\n--- RECOVERABLE ES ERROR ---\n"
                + "Fix the search body: sort and size must be top-level siblings of query, not inside query. "
                + "Retry the execute tool with the corrected query.";
    }

    private static String bareToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        int separator = toolName.indexOf('.');
        return separator > 0 ? toolName.substring(separator + 1) : toolName;
    }
}
