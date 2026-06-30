package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpInvocationContext;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpToolResultPostProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Appends exact filter field paths from RED Elasticsearch sample hits so the LLM does not guess
 * .keyword subfields or wrong dotted paths.
 */
@Component
public class RedQuerySampleResultPostProcessor implements McpToolResultPostProcessor {

    private static final String RED_PREFIX = "red";
    private static final Set<String> SAMPLE_TOOLS = Set.of(
            "red_sample_elasticsearch_query",
            "red_sample_mongo_query"
    );

    @Override
    public boolean supports(McpCatalogEntry entry, String toolName) {
        return RED_PREFIX.equals(entry.serverIdPrefix()) && SAMPLE_TOOLS.contains(bareToolName(toolName));
    }

    @Override
    public String process(
            McpCatalogEntry entry,
            String toolName,
            String rawContent,
            McpInvocationContext context
    ) {
        if (!"red_sample_elasticsearch_query".equals(bareToolName(toolName))) {
            return rawContent;
        }
        RedEsSampleFieldCatalog catalog = RedEsSampleFieldExtractor.parseCatalog(rawContent);
        List<String> paths = catalog.displayPaths();
        if (paths.isEmpty()) {
            return rawContent;
        }
        StringBuilder builder = new StringBuilder(rawContent);
        builder.append("\n\n--- FILTER FIELD PATHS (from sample _source — copy exactly) ---\n");
        for (String path : paths) {
            builder.append("- ").append(path).append('\n');
        }
        builder.append("Do NOT append .keyword unless the path above already includes .keyword.\n");
        builder.append("For array fields marked (array), use a terms query with a JSON array value, not term.\n");
        return builder.toString();
    }

    private static String bareToolName(String toolName) {
        int separator = toolName.indexOf('.');
        return separator > 0 ? toolName.substring(separator + 1) : toolName;
    }
}
