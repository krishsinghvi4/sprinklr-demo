package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.config.LlmSystemPromptLoader;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Appends connected MCP skill sections to the base system prompt for LLM tool-selection passes.
 */
@Component
public class McpSkillPromptAssembler {

    private final LlmSystemPromptLoader systemPromptLoader;
    private final McpSkillPromptLoader skillPromptLoader;

    public McpSkillPromptAssembler(
            LlmSystemPromptLoader systemPromptLoader,
            McpSkillPromptLoader skillPromptLoader
    ) {
        this.systemPromptLoader = systemPromptLoader;
        this.skillPromptLoader = skillPromptLoader;
    }

    /**
     * Builds the system prompt: base copilot instructions plus skill guidance for connected MCP prefixes.
     */
    public String assemble(List<McpTool> activeTools) {
        return assemble(systemPromptLoader.getSystemPrompt(), activeTools);
    }

    /**
     * Appends skill sections for prefixes present in {@code activeTools} to {@code basePrompt}.
     */
    public String assemble(String basePrompt, List<McpTool> activeTools) {
        Set<String> prefixes = extractPrefixes(activeTools);
        if (prefixes.isEmpty()) {
            return basePrompt;
        }

        StringBuilder assembled = new StringBuilder(basePrompt.trim());
        assembled.append("\n\n## Connected MCP guidance");

        for (String prefix : prefixes) {
            skillPromptLoader.findByServerIdPrefix(prefix).ifPresent(skillText -> {
                assembled.append("\n\n### ").append(displayNameForPrefix(prefix)).append("\n");
                assembled.append(skillText.trim());
            });
        }

        return assembled.toString();
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
            default -> prefix.substring(0, 1).toUpperCase() + prefix.substring(1);
        };
    }
}
