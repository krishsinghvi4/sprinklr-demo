package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CompositeMcpLocalToolExtension {

    private final List<McpLocalToolExtension> extensions;

    public CompositeMcpLocalToolExtension(List<McpLocalToolExtension> extensions) {
        this.extensions = extensions;
    }

    public List<McpTool> toolDefinitions(McpCatalogEntry entry, String connectionId) {
        List<McpTool> tools = new ArrayList<>();
        for (McpLocalToolExtension extension : extensions) {
            if (extension.supports(entry)) {
                tools.addAll(extension.toolDefinitions(entry, connectionId));
            }
        }
        return tools;
    }

    public boolean handles(McpCatalogEntry entry, String bareToolName) {
        for (McpLocalToolExtension extension : extensions) {
            if (extension.supports(entry) && extension.handles(entry, bareToolName)) {
                return true;
            }
        }
        return false;
    }

    public String invoke(McpCatalogEntry entry, String bareToolName, McpLocalToolInvocationContext context) {
        for (McpLocalToolExtension extension : extensions) {
            if (extension.supports(entry) && extension.handles(entry, bareToolName)) {
                return extension.invoke(entry, bareToolName, context);
            }
        }
        throw new McpInvocationException(
                "Local tool '" + bareToolName + "' is not registered",
                "No handler for bareToolName=" + bareToolName
        );
    }
}
