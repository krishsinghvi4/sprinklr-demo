package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompositeMcpToolResultPostProcessor {

    private final List<McpToolResultPostProcessor> postProcessors;

    public CompositeMcpToolResultPostProcessor(List<McpToolResultPostProcessor> postProcessors) {
        this.postProcessors = postProcessors;
    }

    public String process(
            McpCatalogEntry entry,
            String toolName,
            String rawContent,
            McpInvocationContext context
    ) {
        for (McpToolResultPostProcessor postProcessor : postProcessors) {
            if (postProcessor.supports(entry, toolName)) {
                return postProcessor.process(entry, toolName, rawContent, context);
            }
        }
        return rawContent;
    }
}
