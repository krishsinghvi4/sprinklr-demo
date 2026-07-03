package com.example.sprinklr.marketplace.application.service.tool;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.PendingWorkflowState;
import com.example.sprinklr.marketplace.domain.model.RouterOutcome;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.model.ToolRouterResult;
import com.example.sprinklr.marketplace.domain.model.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolRouterPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.JiraRedAuditToolSelectionSupport;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.JiraLocalToolSelectionSupport;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogToolSelectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chat-time tool selection: combines the lightweight LLM router (stage 1) with a deterministic
 * dependency expander (stage 2, no LLM) to produce the small set of full-schema tools the agent LLM
 * actually sees.
 */
@Service
public class ToolSelectionService {

    private static final Logger log = LoggerFactory.getLogger(ToolSelectionService.class);

    private final ToolRouterPort toolRouterPort;
    private final McpProperties mcpProperties;
    private final McpCatalogToolSelectionSupport catalogToolSelectionSupport;
    private final JiraLocalToolSelectionSupport jiraLocalToolSelectionSupport;
    private final JiraRedAuditToolSelectionSupport jiraRedAuditToolSelectionSupport;

    public ToolSelectionService(
            ToolRouterPort toolRouterPort,
            McpProperties mcpProperties,
            McpCatalogToolSelectionSupport catalogToolSelectionSupport,
            JiraLocalToolSelectionSupport jiraLocalToolSelectionSupport,
            JiraRedAuditToolSelectionSupport jiraRedAuditToolSelectionSupport
    ) {
        this.toolRouterPort = toolRouterPort;
        this.mcpProperties = mcpProperties;
        this.catalogToolSelectionSupport = catalogToolSelectionSupport;
        this.jiraLocalToolSelectionSupport = jiraLocalToolSelectionSupport;
        this.jiraRedAuditToolSelectionSupport = jiraRedAuditToolSelectionSupport;
    }

    public ToolSelectionResult selectTools(
            String userPrompt,
            List<Message> recentHistory,
            List<McpTool> allUserTools,
            List<ToolDependencyGraph> graphs,
            Optional<PendingWorkflowState> continuation
    ) {
        McpProperties.ToolSelection config = mcpProperties.getToolSelection();

        if (allUserTools == null || allUserTools.isEmpty()) {
            return new ToolSelectionResult(List.of(), List.of(), List.of(), null);
        }

        Map<String, McpTool> toolsByName = new LinkedHashMap<>();
        allUserTools.forEach(tool -> toolsByName.put(tool.name(), tool));

        Map<String, ToolDependencyGraph> readyGraphByPrefix = new LinkedHashMap<>();
        if (graphs != null) {
            for (ToolDependencyGraph graph : graphs) {
                if (graph.status() == DependencyGraphStatus.READY) {
                    readyGraphByPrefix.put(graph.serverIdPrefix(), graph);
                }
            }
        }

        List<Message> routerHistory = trimToRecentTurns(recentHistory, config.getRouterHistoryTurns());
        ToolRouterResult routerResult = toolRouterPort.selectTools(
                userPrompt, routerHistory, allUserTools, config.getRouterMaxPrimaryTools());

        if (routerResult.outcome() == RouterOutcome.FAILED) {
            log.info("[ToolSelection] Router failed — falling back to all {} tools", allUserTools.size());
            return new ToolSelectionResult(
                    List.copyOf(allUserTools),
                    List.copyOf(prefixesOf(toolsByName.keySet())),
                    List.copyOf(toolsByName.keySet()),
                    null);
        }

        List<String> primary = routerResult.toolNames();

        if (routerResult.outcome() == RouterOutcome.NO_TOOLS_NEEDED) {
            log.info("[ToolSelection] Router found no relevant tools — sending zero tools for conversational turn");
            return new ToolSelectionResult(List.of(), List.of(), List.of(), null);
        }

        Set<String> neverSatisfy = catalogToolSelectionSupport.continuationNeverSatisfyToolsForToolNames(
                toolsByName.keySet());
        Optional<PendingWorkflowState> applicableContinuation =
                resolveContinuation(continuation, primary);
        Set<String> satisfied = applicableContinuation
                .map(state -> filterSatisfied(state.satisfiedToolNames(), neverSatisfy))
                .orElseGet(HashSet::new);
        String continuationContext = applicableContinuation
                .map(this::buildContinuationContext)
                .orElse(null);

        List<String> orderedNames = expand(userPrompt, primary, toolsByName, readyGraphByPrefix, satisfied);
        List<String> capped = cap(orderedNames, config.getMaxTools());

        List<McpTool> scopedTools = new ArrayList<>();
        for (String name : capped) {
            McpTool tool = toolsByName.get(name);
            if (tool != null) {
                scopedTools.add(tool);
            }
        }

        List<String> activePrefixes = List.copyOf(prefixesOf(capped));
        log.info("[ToolSelection] primary={} expandedTo={} capped={} scopedTools={} prefixes={} continuation={}",
                primary, orderedNames.size(), scopedTools.size(), capped, activePrefixes,
                applicableContinuation.isPresent());

        return new ToolSelectionResult(scopedTools, activePrefixes, List.copyOf(primary), continuationContext);
    }

    private Set<String> filterSatisfied(List<String> satisfiedToolNames, Set<String> neverSatisfy) {
        Set<String> filtered = new HashSet<>();
        for (String name : satisfiedToolNames) {
            if (!neverSatisfy.contains(name)) {
                filtered.add(name);
            }
        }
        return filtered;
    }

    private List<String> expand(
            String userPrompt,
            List<String> primary,
            Map<String, McpTool> toolsByName,
            Map<String, ToolDependencyGraph> readyGraphByPrefix,
            Set<String> satisfied
    ) {
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> expandedPrimary = new ArrayList<>(primary);
        expandedPrimary.addAll(jiraRedAuditToolSelectionSupport.bridgeTools(
                primary, userPrompt, toolsByName.keySet()));
        for (String toolName : expandedPrimary) {
            if (jiraLocalToolSelectionSupport.hasLocalPrerequisites(toolName)) {
                for (String expandedTool : jiraLocalToolSelectionSupport.prerequisitesFor(toolName, toolsByName.keySet())) {
                    if (satisfied.contains(expandedTool)) {
                        continue;
                    }
                    if (toolsByName.containsKey(expandedTool) && seen.add(expandedTool)) {
                        ordered.add(expandedTool);
                    }
                }
                continue;
            }
            ToolDependencyGraph graph = readyGraphByPrefix.get(prefixOf(toolName));
            if (graph != null) {
                for (String prerequisite : graph.transitivePrerequisites(toolName)) {
                    if (satisfied.contains(prerequisite)) {
                        continue;
                    }
                    if (toolsByName.containsKey(prerequisite) && seen.add(prerequisite)) {
                        ordered.add(prerequisite);
                    }
                }
            }
            if (seen.add(toolName)) {
                ordered.add(toolName);
            }
        }
        return ordered;
    }

    private List<String> cap(List<String> ordered, int maxTools) {
        if (maxTools <= 0 || ordered.size() <= maxTools) {
            return ordered;
        }
        log.info("[ToolSelection] Capping {} tools to {}", ordered.size(), maxTools);
        return new ArrayList<>(ordered.subList(0, maxTools));
    }

    private Optional<PendingWorkflowState> resolveContinuation(
            Optional<PendingWorkflowState> continuation, List<String> currentPrimary) {
        if (continuation.isEmpty()) {
            return Optional.empty();
        }
        PendingWorkflowState state = continuation.get();
        if (state.isExpired(Instant.now())) {
            log.info("[ToolSelection] Ignoring expired continuation state for conversation={}",
                    state.conversationId());
            return Optional.empty();
        }
        if (state.awaitingGoalTools().isEmpty()) {
            log.debug("[ToolSelection] Ignoring continuation without awaitingGoalTools conversation={}",
                    state.conversationId());
            return Optional.empty();
        }
        Set<String> primarySet = new HashSet<>(currentPrimary);
        boolean relevant = state.awaitingGoalTools().stream().anyMatch(primarySet::contains);
        if (!relevant) {
            log.info("[ToolSelection] Continuation awaiting {} not relevant to current primary {}",
                    state.awaitingGoalTools(), currentPrimary);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    private String buildContinuationContext(PendingWorkflowState state) {
        if (state.toolResultSummaries().isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(
                "Workflow context from the previous turn — reuse this intent and do NOT re-ask the user "
                        + "for information they already provided:\n");
        for (String summary : state.toolResultSummaries()) {
            builder.append("- ").append(summary).append('\n');
        }
        return builder.toString();
    }

    private List<Message> trimToRecentTurns(List<Message> history, int turns) {
        if (history == null || history.isEmpty() || turns <= 0) {
            return List.of();
        }
        int userTurnsSeen = 0;
        int startIndex = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == MessageRole.USER) {
                userTurnsSeen++;
                if (userTurnsSeen >= turns) {
                    startIndex = i;
                    break;
                }
            }
        }
        return new ArrayList<>(history.subList(startIndex, history.size()));
    }

    private Set<String> prefixesOf(Iterable<String> toolNames) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String name : toolNames) {
            prefixes.add(prefixOf(name));
        }
        return prefixes;
    }

    private String prefixOf(String toolName) {
        int dot = toolName.indexOf('.');
        return dot > 0 ? toolName.substring(0, dot) : toolName;
    }
}
