package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpInvocationContext;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpToolResultPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Appends retry guidance when RED Elasticsearch execute tools return recoverable DSL errors.
 */
@Component
public class RedEsQueryExecuteResultPostProcessor implements McpToolResultPostProcessor {

    private static final String RED_PREFIX = "red";
    private final RedEsQueryRetrySupport retrySupport;

    public RedEsQueryExecuteResultPostProcessor(RedEsQueryRetrySupport retrySupport) {
        this.retrySupport = retrySupport;
    }

    @Override
    public boolean supports(McpCatalogEntry entry, String toolName) {
        return RED_PREFIX.equals(entry.serverIdPrefix()) && retrySupport.isRedElasticsearchExecuteTool(toolName);
    }

    @Override
    public String process(
            McpCatalogEntry entry,
            String toolName,
            String rawContent,
            McpInvocationContext context
    ) {
        return retrySupport.appendRecoverableErrorHint(rawContent);
    }
}
