package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.RedQueryToolSelectionSupport;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight.McpInvocationPreflightStrategy;
import org.springframework.stereotype.Component;

/**
 * Blocks RED execute query tools until the corresponding sample tool has run this turn.
 */
@Component
public class RedQueryPreflightGuard implements McpInvocationPreflightStrategy {

    private static final String RED_PREFIX = "red";

    private final RedQueryToolSelectionSupport redQueryToolSelectionSupport;

    public RedQueryPreflightGuard(RedQueryToolSelectionSupport redQueryToolSelectionSupport) {
        this.redQueryToolSelectionSupport = redQueryToolSelectionSupport;
    }

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        if (!isRedExecuteQueryTool(invocation)) {
            return PreflightResult.allow();
        }

        String fullyQualified = fullyQualifiedName(invocation);
        String sampleTool = redQueryToolSelectionSupport.sampleToolForExecute(fullyQualified).orElse(null);
        if (sampleTool == null) {
            return PreflightResult.allow();
        }

        String context = userPrompt == null ? "" : userPrompt;
        if (sampleCalledThisTurn(context, sampleTool)) {
            return PreflightResult.allow();
        }

        return PreflightResult.block(
                "Call " + bareToolName(sampleTool) + " first in this turn to discover field names, "
                        + "then call " + bareToolName(fullyQualified) + " with a filter built from those samples."
        );
    }

    private boolean isRedExecuteQueryTool(McpInvocation invocation) {
        if (invocation.toolName() == null) {
            return false;
        }
        String bare = bareToolName(invocation.toolName());
        return "red_execute_mongo_query".equals(bare)
                || "red_execute_elastic_search_query".equals(bare);
    }

    private String fullyQualifiedName(McpInvocation invocation) {
        if (invocation.toolName().contains(".")) {
            return invocation.toolName();
        }
        return RED_PREFIX + "." + invocation.toolName();
    }

    private static String bareToolName(String toolName) {
        int separator = toolName.indexOf('.');
        return separator > 0 ? toolName.substring(separator + 1) : toolName;
    }

    private static final String TOOLS_CALLED_MARKER = "[tools-called-this-turn:";

    static boolean sampleCalledThisTurn(String context, String sampleTool) {
        String toolsList = extractToolsCalledMarker(context);
        if (toolsList.isEmpty()) {
            return false;
        }
        String bareSample = bareToolName(sampleTool);
        for (String called : toolsList.split(",")) {
            String trimmed = called.trim();
            if (trimmed.equals(sampleTool) || trimmed.equals(bareSample)) {
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
}
