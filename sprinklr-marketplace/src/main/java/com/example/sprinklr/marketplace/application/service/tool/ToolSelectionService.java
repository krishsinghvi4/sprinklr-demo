package com.example.sprinklr.marketplace.application.service.tool;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.PendingWorkflowState;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.model.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolRouterPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
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
 * <p>
 * Pipeline per turn:
 * <ol>
 *   <li>Router picks the primary tools relevant to the prompt (names + descriptions only).</li>
 *   <li>Expander walks each tool's stored dependency graph and adds prerequisites in topological order.</li>
 *   <li>Continuation: prerequisites already satisfied in an earlier turn of the same workflow are skipped,
 *       and their result summaries are returned for injection into the agent context.</li>
 *   <li>The set is capped (default 15) and resolved to {@link McpTool} records with schemas.</li>
 * </ol>
 * Fails soft: if the router returns nothing (error, or genuinely no relevant tool), it falls back to the
 * legacy all-tools behavior so chat is never broken.
 */
@Service
public class ToolSelectionService {

    private static final Logger log = LoggerFactory.getLogger(ToolSelectionService.class);

    private final ToolRouterPort toolRouterPort;
    private final McpProperties mcpProperties;

    public ToolSelectionService(ToolRouterPort toolRouterPort, McpProperties mcpProperties) {
        this.toolRouterPort = toolRouterPort;
        this.mcpProperties = mcpProperties;
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
            return new ToolSelectionResult(List.of(), List.of(), null);
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
        List<String> primary = toolRouterPort.selectToolNames(
                userPrompt, routerHistory, allUserTools, config.getRouterMaxPrimaryTools());

        // Decide whether stored continuation belongs to this turn, based on the servers in play.
        Set<String> candidatePrefixes = prefixesOf(primary.isEmpty()
                ? new ArrayList<>(toolsByName.keySet())
                : primary);
        Optional<PendingWorkflowState> applicableContinuation =
                resolveContinuation(continuation, candidatePrefixes);
        Set<String> satisfied = applicableContinuation
                .map(state -> new HashSet<>(state.satisfiedToolNames()))
                .orElseGet(HashSet::new);
        String continuationContext = applicableContinuation
                .map(this::buildContinuationContext)
                .orElse(null);

        if (primary.isEmpty()) {
            // Router found nothing relevant (or failed) — preserve existing behavior: expose all tools.
            log.info("[ToolSelection] Router returned no tools — falling back to all {} tools", allUserTools.size());
            return new ToolSelectionResult(
                    List.copyOf(allUserTools), List.copyOf(prefixesOf(toolsByName.keySet())), continuationContext);
        }

        List<String> orderedNames = expand(primary, toolsByName, readyGraphByPrefix, satisfied);
        List<String> capped = cap(orderedNames, config.getMaxTools());

        List<McpTool> scopedTools = new ArrayList<>();
        for (String name : capped) {
            McpTool tool = toolsByName.get(name);
            if (tool != null) {
                scopedTools.add(tool);
            }
        }

        List<String> activePrefixes = List.copyOf(prefixesOf(capped));
        log.info("[ToolSelection] primary={} expandedTo={} capped={} prefixes={} continuation={}",
                primary.size(), orderedNames.size(), scopedTools.size(), activePrefixes,
                applicableContinuation.isPresent());

        return new ToolSelectionResult(scopedTools, activePrefixes, continuationContext);
    }

    /**
     * Expands router picks with their prerequisites in topological order (prerequisites first), skipping
     * any prerequisite already satisfied by continuation state. Each tool's prerequisites come only from
     * its own server's READY graph.
     */
    private List<String> expand(
            List<String> primary,
            Map<String, McpTool> toolsByName,
            Map<String, ToolDependencyGraph> readyGraphByPrefix,
            Set<String> satisfied
    ) {
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String toolName : primary) {
            ToolDependencyGraph graph = readyGraphByPrefix.get(prefixOf(toolName));
            if (graph != null) {
                for (String prerequisite : graph.transitivePrerequisites(toolName)) {
                    if (satisfied.contains(prerequisite)) {
                        continue; // already gathered in an earlier turn of this workflow
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

    /** Keeps prerequisites (front of the list) first when trimming to the cap. */
    private List<String> cap(List<String> ordered, int maxTools) {
        if (maxTools <= 0 || ordered.size() <= maxTools) {
            return ordered;
        }
        log.info("[ToolSelection] Capping {} tools to {}", ordered.size(), maxTools);
        return new ArrayList<>(ordered.subList(0, maxTools));
    }

    private Optional<PendingWorkflowState> resolveContinuation(
            Optional<PendingWorkflowState> continuation, Set<String> candidatePrefixes) {
        if (continuation.isEmpty()) {
            return Optional.empty();
        }
        PendingWorkflowState state = continuation.get();
        if (state.isExpired(Instant.now())) {
            log.info("[ToolSelection] Ignoring expired continuation state for conversation={}",
                    state.conversationId());
            return Optional.empty();
        }
        boolean relevant = state.serverPrefixes().isEmpty()
                || state.serverPrefixes().stream().anyMatch(candidatePrefixes::contains);
        if (!relevant) {
            log.debug("[ToolSelection] Continuation prefixes {} not relevant to {}",
                    state.serverPrefixes(), candidatePrefixes);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    private String buildContinuationContext(PendingWorkflowState state) {
        if (state.toolResultSummaries().isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(
                "Information already gathered earlier in this workflow — reuse it and do NOT re-run these "
                        + "tools to fetch it again:\n");
        for (String summary : state.toolResultSummaries()) {
            builder.append("- ").append(summary).append('\n');
        }
        return builder.toString();
    }

    /** Keeps only the messages belonging to the last {@code turns} user-initiated turns, for router context. */
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
