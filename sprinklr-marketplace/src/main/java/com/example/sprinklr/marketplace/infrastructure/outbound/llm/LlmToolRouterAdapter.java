package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolRouterPort;
import com.example.sprinklr.marketplace.infrastructure.config.LlmSystemPromptLoader;
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
 * Chat-time "stage 1" tool router. Runs a single cheap, text-only LLM call over a compact catalog
 * (tool names + descriptions only — never schemas) and returns the primary tools relevant to the turn.
 * <p>
 * Designed to fail soft: any error or unparseable response yields an empty selection so the caller
 * ({@link com.example.sprinklr.marketplace.application.service.tool.ToolSelectionService}) can fall back
 * to the legacy all-tools behavior rather than breaking the chat.
 */
@Component
public class LlmToolRouterAdapter implements ToolRouterPort {

    private static final Logger log = LoggerFactory.getLogger(LlmToolRouterAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmService llmService;
    private final LlmSystemPromptLoader promptLoader;

    public LlmToolRouterAdapter(LlmService llmService, LlmSystemPromptLoader promptLoader) {
        this.llmService = llmService;
        this.promptLoader = promptLoader;
    }

    @Override
    public List<String> selectToolNames(
            String userPrompt,
            List<Message> recentHistory,
            List<McpTool> availableTools,
            int maxPrimaryTools
    ) {
        if (availableTools == null || availableTools.isEmpty()) {
            return List.of();
        }

        Set<String> validNames = new LinkedHashSet<>();
        availableTools.forEach(tool -> validNames.add(tool.name()));

        String systemPrompt = promptLoader.getToolRouterPrompt()
                + "\n\n## Available tools (name: description)\n" + buildCompactCatalog(availableTools)
                + "\n\nSelect at most " + maxPrimaryTools + " primary tools.";
        List<Message> request = List.of(syntheticUserMessage(buildUserBlock(userPrompt, recentHistory)));

        //fall back to sending all tool if try block fails
        try {
            long startMs = System.currentTimeMillis();
            LlmCompletionResult result = llmService.complete(
                    new LlmCompletionCommand(request, List.of(), null, false, systemPrompt));
            List<String> selected = parseSelection(result.content(), validNames);
            log.info("[ToolRouter] Selected {} of {} tools in {}ms: {}",
                    selected.size(), availableTools.size(), System.currentTimeMillis() - startMs, selected);
            return selected;
        } catch (Exception e) {
            log.warn("[ToolRouter] Routing failed — caller will fall back to all tools: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseSelection(String content, Set<String> validNames) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String json = stripFences(content.trim());
        List<String> selected = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode array = root.path("selectedTools");
            if (!array.isArray()) {
                log.warn("[ToolRouter] Response missing 'selectedTools' array");
                return List.of();
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
            return List.of();
        }
        return selected;
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
