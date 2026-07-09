package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.example.sprinklr.marketplace.domain.model.chat.MessageRole;
import com.example.sprinklr.marketplace.domain.model.LLM.RouterOutcome;
import com.example.sprinklr.marketplace.domain.model.tool.ToolRouterResult;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolRouterPort;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmSystemPromptLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.JiraRedAuditToolSelectionSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Chat-time "stage 1" tool router. Runs a two-pass LLM flow over a compact catalog
 * (tool names + descriptions only — never schemas):
 * <ol>
 *   <li>Pass 1: catalog only — initial primary tool picks.</li>
 *   <li>Pass 2: prefix-scoped workflow skills from {@code llm/mcp-skills/} — refine and add primaries.</li>
 * </ol>
 * <p>
 * Designed to fail soft: LLM or parse failures yield {@link RouterOutcome#FAILED} so the caller can fall
 * back to all tools. A valid empty selection yields {@link RouterOutcome#NO_TOOLS_NEEDED}.
 */
@Component
public class LlmToolRouterAdapter implements ToolRouterPort {

    private static final Logger log = LoggerFactory.getLogger(LlmToolRouterAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmService llmService;
    private final LlmSystemPromptLoader promptLoader;
    private final McpSkillPromptAssembler skillPromptAssembler;
    private final JiraRedAuditToolSelectionSupport jiraRedAuditToolSelectionSupport;

    public LlmToolRouterAdapter(
            LlmService llmService,
            LlmSystemPromptLoader promptLoader,
            McpSkillPromptAssembler skillPromptAssembler,
            JiraRedAuditToolSelectionSupport jiraRedAuditToolSelectionSupport
    ) {
        this.llmService = llmService;
        this.promptLoader = promptLoader;
        this.skillPromptAssembler = skillPromptAssembler;
        this.jiraRedAuditToolSelectionSupport = jiraRedAuditToolSelectionSupport;
    }

    @Override
    public ToolRouterResult selectTools(
            String userPrompt,
            List<Message> recentHistory,
            List<McpTool> availableTools,
            int maxPrimaryTools,
            String userId
    ) {
        if (availableTools == null || availableTools.isEmpty()) {
            return ToolRouterResult.noToolsNeeded();
        }

        Set<String> validNames = new LinkedHashSet<>();
        availableTools.forEach(tool -> validNames.add(tool.name()));

        String baseRouterPrompt = promptLoader.getToolRouterPrompt();
        String catalogSection = "\n\n## Available tools (name: description)\n"
                + buildCompactCatalog(availableTools)
                + "\n\nSelect at most " + maxPrimaryTools + " primary tools.";
        String pass1SystemPrompt = baseRouterPrompt + catalogSection;
        String userBlock = buildUserBlock(userPrompt, recentHistory);

        try {
            long startMs = System.currentTimeMillis();
            ToolRouterResult pass1 = runRouterCall(pass1SystemPrompt, userBlock, validNames);
            log.info("[ToolRouter] pass1 outcome={} selected={} of {} tools in {}ms",
                    pass1.outcome(), pass1.toolNames().size(), availableTools.size(),
                    System.currentTimeMillis() - startMs);

            if (pass1.outcome() != RouterOutcome.TOOLS_SELECTED || pass1.toolNames().isEmpty()) {
                return pass1;
            }

            Set<String> skillPrefixes = resolveSkillPrefixes(pass1.toolNames(), userPrompt, availableTools);
            if (!skillPromptAssembler.hasGuidanceForPrefixes(skillPrefixes, userId)) {
                log.info("[ToolRouter] No workflow skills for prefixes {} — skipping pass2", skillPrefixes);
                return pass1;
            }

            String pass2SystemPrompt = skillPromptAssembler.assembleForPrefixes(baseRouterPrompt, skillPrefixes, userId)
                    + catalogSection;

            String refinementUserBlock = userBlock + "\n\nInitial tool selection from pass 1:\n"
                    + formatToolList(pass1.toolNames())
                    + "\nReview the workflow guidance. Add any additional PRIMARY tools needed for this request.\n"
                    + "Keep correct tools from the initial selection; replace only if clearly wrong.";

            long pass2StartMs = System.currentTimeMillis();
            ToolRouterResult pass2 = runRouterCall(pass2SystemPrompt, refinementUserBlock, validNames);
            log.info("[ToolRouter] pass2 outcome={} selected={} prefixes={} in {}ms",
                    pass2.outcome(), pass2.toolNames().size(), skillPrefixes,
                    System.currentTimeMillis() - pass2StartMs);

            if (pass2.outcome() != RouterOutcome.TOOLS_SELECTED || pass2.toolNames().isEmpty()) {
                log.info("[ToolRouter] pass2 failed or empty — using pass1 selection");
                return pass1;
            }

            List<String> merged = mergeSelections(pass1.toolNames(), pass2.toolNames(), maxPrimaryTools);
            log.info("[ToolRouter] merged pass1={} pass2={} final={}", pass1.toolNames(), pass2.toolNames(), merged);
            return ToolRouterResult.selected(merged);
        } catch (Exception e) {
            log.warn("[ToolRouter] Routing failed — caller will fall back to all tools: {}", e.getMessage());
            return ToolRouterResult.failed();
        }
    }

    private ToolRouterResult runRouterCall(String systemPrompt, String userBlock, Set<String> validNames) {
        List<Message> request = List.of(syntheticUserMessage(userBlock));
        LlmCompletionResult result = llmService.complete(
                new LlmCompletionCommand(request, List.of(), null, false, systemPrompt));
        return parseSelection(result.content(), validNames);
    }

    private Set<String> resolveSkillPrefixes(
            List<String> pass1ToolNames,
            String userPrompt,
            List<McpTool> availableTools
    ) {
        Set<String> prefixes = extractPrefixes(pass1ToolNames);
        if (jiraRedAuditToolSelectionSupport.matchesAuditInvestigationIntent(userPrompt)) {
            if (hasToolsWithPrefix(availableTools, "jira")) {
                prefixes.add("jira");
            }
            if (hasToolsWithPrefix(availableTools, "red")) {
                prefixes.add("red");
            }
        }
        return prefixes;
    }

    private static boolean hasToolsWithPrefix(List<McpTool> tools, String prefix) {
        String dottedPrefix = prefix + ".";
        return tools.stream().anyMatch(tool -> tool.name().startsWith(dottedPrefix));
    }

    private static Set<String> extractPrefixes(List<String> toolNames) {
        Set<String> prefixes = new LinkedHashSet<>();
        if (toolNames == null) {
            return prefixes;
        }
        for (String toolName : toolNames) {
            int separator = toolName.indexOf('.');
            if (separator > 0) {
                prefixes.add(toolName.substring(0, separator));
            }
        }
        return prefixes;
    }

    private static List<String> mergeSelections(List<String> pass1, List<String> pass2, int maxPrimaryTools) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> merged = new ArrayList<>();
        for (String name : pass1) {
            if (seen.add(name)) {
                merged.add(name);
            }
        }
        for (String name : pass2) {
            if (seen.add(name)) {
                merged.add(name);
            }
        }
        if (maxPrimaryTools > 0 && merged.size() > maxPrimaryTools) {
            return new ArrayList<>(merged.subList(0, maxPrimaryTools));
        }
        return merged;
    }

    private static String formatToolList(List<String> toolNames) {
        StringBuilder builder = new StringBuilder();
        for (String name : toolNames) {
            builder.append("- ").append(name).append('\n');
        }
        return builder.toString();
    }

    private ToolRouterResult parseSelection(String content, Set<String> validNames) {
        if (content == null || content.isBlank()) {
            return ToolRouterResult.failed();
        }
        String json = stripFences(content.trim());
        List<String> selected = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode array = root.path("selectedTools");
            if (!array.isArray()) {
                log.warn("[ToolRouter] Response missing 'selectedTools' array");
                return ToolRouterResult.failed();
            }
            for (JsonNode node : array) {
                String name = node.asText();
                if (validNames.contains(name) && !selected.contains(name)) {
                    selected.add(name);
                } else if (!validNames.contains(name)) {
                    log.debug("[ToolRouter] Dropping unknown tool from selection: {}", name);
                }
            }
        } catch (Exception e) {
            log.warn("[ToolRouter] Failed to parse selection JSON: {}", e.getMessage());
            return ToolRouterResult.failed();
        }
        if (selected.isEmpty()) {
            return ToolRouterResult.noToolsNeeded();
        }
        return ToolRouterResult.selected(selected);
    }

    private String buildCompactCatalog(List<McpTool> tools) {
        StringBuilder builder = new StringBuilder();
        for (McpTool tool : tools) {
            builder.append("- ").append(tool.name()).append(": ")
                    .append(firstLine(tool.description())).append('\n');
        }
        return builder.toString();
    }

    private String buildUserBlock(String userPrompt, List<Message> recentHistory) {
        StringBuilder builder = new StringBuilder();
        if (recentHistory != null && !recentHistory.isEmpty()) {
            builder.append("Recent conversation:\n");
            for (Message message : recentHistory) {
                if (message.role() != MessageRole.USER && message.role() != MessageRole.ASSISTANT) {
                    continue;
                }
                if (message.content() == null || message.content().isBlank()) {
                    continue;
                }
                builder.append(message.role() == MessageRole.USER ? "User: " : "Assistant: ")
                        .append(firstLine(message.content())).append('\n');
            }
            builder.append('\n');
        }
        builder.append("Current user request:\n").append(userPrompt);
        return builder.toString();
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        String line = newline >= 0 ? text.substring(0, newline) : text;
        return line.length() <= 300 ? line : line.substring(0, 300) + "...";
    }

    private String stripFences(String text) {
        String stripped = text;
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline >= 0) {
                stripped = stripped.substring(firstNewline + 1);
            }
            int closingFence = stripped.lastIndexOf("```");
            if (closingFence >= 0) {
                stripped = stripped.substring(0, closingFence);
            }
        }
        return stripped.trim();
    }

    private Message syntheticUserMessage(String content) {
        return new Message(
                UUID.randomUUID().toString(),
                "tool-router",
                MessageRole.USER,
                content,
                List.of(),
                List.of(),
                Instant.now()
        );
    }
}
