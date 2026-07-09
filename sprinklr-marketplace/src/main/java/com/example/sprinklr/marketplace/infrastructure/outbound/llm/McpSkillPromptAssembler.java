package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmSystemPromptLoader;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Appends connected MCP skill sections to the base system prompt for LLM tool-selection passes.
 */
@Component
public class McpSkillPromptAssembler {

    private final LlmSystemPromptLoader systemPromptLoader;
    private final McpSkillPromptLoader skillPromptLoader;
    private final CrossWorkflowSkillLoader crossWorkflowSkillLoader;
    private final UserMcpSkillProvider userMcpSkillProvider;

    public McpSkillPromptAssembler(
            LlmSystemPromptLoader systemPromptLoader,
            McpSkillPromptLoader skillPromptLoader,
            CrossWorkflowSkillLoader crossWorkflowSkillLoader,
            UserMcpSkillProvider userMcpSkillProvider
    ) {
        this.systemPromptLoader = systemPromptLoader;
        this.skillPromptLoader = skillPromptLoader;
        this.crossWorkflowSkillLoader = crossWorkflowSkillLoader;
        this.userMcpSkillProvider = userMcpSkillProvider;
    }

    /**
     * Builds the system prompt: base copilot instructions plus skill guidance for connected MCP prefixes.
     */
    public String assemble(List<McpTool> activeTools) {
        return assemble(systemPromptLoader.getSystemPrompt(), activeTools, null);
    }

    /**
     * Appends skill sections for prefixes present in {@code activeTools} to {@code basePrompt}.
     */
    public String assemble(String basePrompt, List<McpTool> activeTools) {
        return assemble(basePrompt, activeTools, null);
    }

    public String assemble(String basePrompt, List<McpTool> activeTools, String userId) {
        return assembleForPrefixes(basePrompt, extractPrefixes(activeTools), "## Connected MCP guidance", userId);
    }

    /**
     * Appends skill sections for the given server prefixes to {@code basePrompt}.
     * Used by the tool router refinement pass to inject workflow guidance without loading all skills.
     */
    public String assembleForPrefixes(String basePrompt, Set<String> prefixes) {
        return assembleForPrefixes(basePrompt, prefixes, "## Workflow guidance for routing", null);
    }

    public String assembleForPrefixes(String basePrompt, Set<String> prefixes, String userId) {
        return assembleForPrefixes(basePrompt, prefixes, "## Workflow guidance for routing", userId);
    }

    /**
     * Returns true when at least one per-server or cross-workflow skill exists for {@code prefixes}.
     */
    public boolean hasGuidanceForPrefixes(Set<String> prefixes) {
        return hasGuidanceForPrefixes(prefixes, null);
    }

    public boolean hasGuidanceForPrefixes(Set<String> prefixes, String userId) {
        if (prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (skillPromptLoader.findByServerIdPrefix(prefix).isPresent()) {
                return true;
            }
            if (userId != null && userMcpSkillProvider.findByServerIdPrefix(userId, prefix).isPresent()) {
                return true;
            }
        }
        return !crossWorkflowSkillLoader.findMatching(prefixes).isEmpty();
    }

    private String assembleForPrefixes(
            String basePrompt,
            Set<String> prefixes,
            String sectionHeading,
            String userId
    ) {
        if (prefixes == null || prefixes.isEmpty()) {
            return basePrompt;
        }

        StringBuilder assembled = new StringBuilder(basePrompt.trim());
        assembled.append("\n\n").append(sectionHeading);

        Map<String, String> userSkills = userId == null
                ? Map.of()
                : userMcpSkillProvider.findSkillsForActiveConnections(userId, List.copyOf(prefixes));

        for (String prefix : prefixes) {
            java.util.Optional<String> skillText = skillPromptLoader.findByServerIdPrefix(prefix);
            if (skillText.isEmpty() && userSkills.containsKey(prefix)) {
                skillText = java.util.Optional.of(userSkills.get(prefix));
            }
            if (skillText.isEmpty()) {
                continue;
            }
            assembled.append("\n\n### ").append(displayNameForPrefix(prefix)).append("\n");
            assembled.append(skillText.get().trim());
        }

        appendCrossWorkflowSkills(assembled, prefixes);

        return assembled.toString();
    }

    private void appendCrossWorkflowSkills(StringBuilder assembled, Set<String> prefixes) {
        List<CrossWorkflowSkillLoader.LoadedCrossWorkflowSkill> matching =
                crossWorkflowSkillLoader.findMatching(prefixes);
        if (matching.isEmpty()) {
            return;
        }
        assembled.append("\n\n### Cross-server workflows");
        for (CrossWorkflowSkillLoader.LoadedCrossWorkflowSkill skill : matching) {
            assembled.append("\n\n#### ").append(skill.config().title()).append("\n");
            assembled.append(skill.skillText().trim());
        }
    }

    private static Set<String> extractPrefixes(List<McpTool> activeTools) {
        Set<String> prefixes = new LinkedHashSet<>();
        if (activeTools == null) {
            return prefixes;
        }
        for (McpTool tool : activeTools) {
            int separator = tool.name().indexOf('.');
            if (separator > 0) {
                prefixes.add(tool.name().substring(0, separator));
            }
        }
        return prefixes;
    }

    private static String displayNameForPrefix(String prefix) {
        return switch (prefix) {
            case "jira" -> "Jira";
            case "gitlab" -> "GitLab";
            case "red" -> "RED";
            default -> prefix.substring(0, 1).toUpperCase() + prefix.substring(1);
        };
    }
}
