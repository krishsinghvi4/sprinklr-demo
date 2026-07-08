package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic, server-agnostic safety net that blocks a tool call when its stored dependency graph says a
 * prerequisite has not run yet this turn. Works for ANY MCP server from its connect-time graph — there
 * is no Jira/GitLab-specific Java here.
 * <p>
 * Disabled by default ({@code app.mcp.tool-selection.dependency-preflight-enabled=false}) because the
 * scoped tool set, topological ordering, and skill prompts are the primary ordering mechanisms. It is an
 * opt-in hard gate; when enabled it relies on the orchestrator tagging the preflight context with the
 * tool names already called this turn ({@code [tools-called-this-turn: ...]}).
 */
@Component
public class ToolDependencyPreflightGuard implements McpInvocationPreflightStrategy {

    private static final Logger log = LoggerFactory.getLogger(ToolDependencyPreflightGuard.class);

    private final McpRegistryPort registryPort;
    private final McpProperties mcpProperties;

    public ToolDependencyPreflightGuard(
            McpRegistryPort registryPort,
            McpProperties mcpProperties
    ) {
        this.registryPort = registryPort;
        this.mcpProperties = mcpProperties;
    }

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        if (!mcpProperties.getToolSelection().isDependencyPreflightEnabled()) {
            return PreflightResult.allow();
        }

        Optional<ToolDependencyGraph> maybeGraph =
                registryPort.findDependencyGraphByConnectionId(invocation.serverId());
        if (maybeGraph.isEmpty()) {
            return PreflightResult.allow();
        }
        ToolDependencyGraph graph = maybeGraph.get();

        String fullyQualifiedName = graph.serverIdPrefix() + "." + invocation.toolName();
        List<String> prerequisites = graph.transitivePrerequisites(fullyQualifiedName);
        if (prerequisites.isEmpty()) {
            return PreflightResult.allow();
        }

        String context = userPrompt == null ? "" : userPrompt;
        List<String> missing = new ArrayList<>();
        for (String prerequisite : prerequisites) {
            if (context.contains(prerequisite)) {
                continue;
            }
            missing.add(prerequisite);
        }
        if (missing.isEmpty()) {
            return PreflightResult.allow();
        }

        log.info("[Preflight] Dependency guard blocking tool={} missingPrerequisites={}",
                fullyQualifiedName, missing);
        return PreflightResult.block(
                "Call the prerequisite tool(s) first in this turn before " + fullyQualifiedName + ": "
                        + String.join(", ", missing) + ".");
    }
}
