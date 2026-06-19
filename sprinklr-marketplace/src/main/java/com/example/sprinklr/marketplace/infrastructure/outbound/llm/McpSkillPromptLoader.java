package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads per-MCP skill prompt text from catalog {@code llmSkillPath} entries at startup,
 * keyed by {@code serverIdPrefix} (e.g. {@code jira}, {@code gitlab}).
 */
@Component
public class McpSkillPromptLoader {

    private final Map<String, String> skillsByPrefix;

    public McpSkillPromptLoader(McpCatalogLoader catalogLoader, ResourceLoader resourceLoader) {
        Map<String, String> loaded = new LinkedHashMap<>();
        for (McpCatalogEntry entry : catalogLoader.getAll()) {
            String skillPath = entry.llmSkillPath();
            if (skillPath == null || skillPath.isBlank()) {
                continue;
            }
            loaded.put(entry.serverIdPrefix(), loadSkill(entry.id(), skillPath, resourceLoader));
        }
        this.skillsByPrefix = Map.copyOf(loaded);
    }

    public Optional<String> findByServerIdPrefix(String serverIdPrefix) {
        if (serverIdPrefix == null || serverIdPrefix.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skillsByPrefix.get(serverIdPrefix));
    }

    private static String loadSkill(String catalogId, String skillPath, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(skillPath);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "MCP skill prompt not found for catalog entry '" + catalogId + "' at: " + skillPath);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read MCP skill prompt for catalog entry '" + catalogId + "' from: " + skillPath,
                    exception);
        }
    }
}
