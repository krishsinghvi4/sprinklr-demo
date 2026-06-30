package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.RedQueryToolSelectionSupport;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects incomplete RED sample-then-execute workflows within a single chat turn.
 */
@Component
public class RedQueryWorkflowSupport {

    private final RedQueryToolSelectionSupport redQueryToolSelectionSupport;

    public RedQueryWorkflowSupport(RedQueryToolSelectionSupport redQueryToolSelectionSupport) {
        this.redQueryToolSelectionSupport = redQueryToolSelectionSupport;
    }

    public Optional<String> pendingExecuteAfterSample(List<McpTool> scopedTools, List<String> executedToolNames) {
        Set<String> scopedNames = scopedTools.stream().map(McpTool::name).collect(Collectors.toSet());
        Set<String> executed = normalizeToolNames(executedToolNames);

        for (String scopedName : scopedNames) {
            if (!redQueryToolSelectionSupport.isQueryPairTool(scopedName)) {
                continue;
            }
            List<String> pair = redQueryToolSelectionSupport.expandQueryPair(scopedName, scopedNames);
            if (pair.size() < 2) {
                continue;
            }
            String sampleTool = pair.get(0);
            String executeTool = pair.get(1);
            if (executed.contains(sampleTool) && scopedNames.contains(executeTool) && !executed.contains(executeTool)) {
                return Optional.of(executeTool);
            }
        }
        return Optional.empty();
    }

    /**
     * Ensures the execute half of a RED query pair is exposed to the LLM after sample ran, even when
     * continuation state incorrectly omitted it from the initial scoped set.
     */
    public void ensureExecuteToolInScope(
            List<McpTool> toolsForTurn,
            List<McpTool> allUserTools,
            List<String> executedToolNames
    ) {
        Set<String> scopedNames = toolsForTurn.stream().map(McpTool::name).collect(Collectors.toSet());
        Set<String> executed = normalizeToolNames(executedToolNames);
        Map<String, McpTool> toolsByName = allUserTools.stream()
                .collect(Collectors.toMap(McpTool::name, tool -> tool, (left, right) -> left, LinkedHashMap::new));

        for (String scopedName : scopedNames) {
            if (!redQueryToolSelectionSupport.isQueryPairTool(scopedName)) {
                continue;
            }
            List<String> pair = redQueryToolSelectionSupport.expandQueryPair(scopedName, toolsByName.keySet());
            if (pair.size() < 2) {
                continue;
            }
            String sampleTool = pair.get(0);
            String executeTool = pair.get(1);
            if (executed.contains(sampleTool) && !scopedNames.contains(executeTool)) {
                McpTool execute = toolsByName.get(executeTool);
                if (execute != null) {
                    toolsForTurn.add(execute);
                    scopedNames.add(executeTool);
                }
            }
        }
    }

    public String buildExecuteNudge(String existingContext, String executeToolName) {
        String bareName = bareToolName(executeToolName);
        String nudge = "RED workflow incomplete: you already ran the sample query but have NOT called "
                + bareName + " yet. Sample rows are unfiltered schema probes only — do NOT present them as "
                + "the user's filtered answer. Call " + bareName
                + " now with a filter built from sample field names and values from the user's request.";
        if (existingContext == null || existingContext.isBlank()) {
            return nudge;
        }
        return existingContext + "\n\n" + nudge;
    }

    private static String bareToolName(String toolName) {
        int separator = toolName.indexOf('.');
        return separator > 0 ? toolName.substring(separator + 1) : toolName;
    }

    /** Accepts bare MCP names (red_sample_*) and catalog-prefixed names (red.red_sample_*). */
    private static Set<String> normalizeToolNames(List<String> toolNames) {
        Set<String> normalized = new HashSet<>();
        for (String name : toolNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            normalized.add(name);
            if (name.contains(".")) {
                normalized.add(name.substring(name.indexOf('.') + 1));
            } else {
                normalized.add("red." + name);
            }
        }
        return normalized;
    }
}
