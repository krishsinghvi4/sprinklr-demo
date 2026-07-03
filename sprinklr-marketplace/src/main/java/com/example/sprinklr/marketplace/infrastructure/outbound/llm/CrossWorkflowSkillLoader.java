package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.CrossWorkflowSkillConfig;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Loads cross-server workflow skill text from catalog {@code crossWorkflowSkills} entries at startup.
 */
@Component
public class CrossWorkflowSkillLoader {

    private final List<LoadedCrossWorkflowSkill> skills;

    public CrossWorkflowSkillLoader(McpCatalogLoader catalogLoader, ResourceLoader resourceLoader) {
        List<LoadedCrossWorkflowSkill> loaded = new ArrayList<>();
        for (CrossWorkflowSkillConfig config : catalogLoader.getCrossWorkflowSkills()) {
            loaded.add(new LoadedCrossWorkflowSkill(
                    config,
                    loadSkill(config.id(), config.skillPath(), resourceLoader)
            ));
        }
        this.skills = List.copyOf(loaded);
    }

    public List<LoadedCrossWorkflowSkill> findMatching(Set<String> activePrefixes) {
        if (activePrefixes == null || activePrefixes.isEmpty()) {
            return List.of();
        }
        return skills.stream()
                .filter(skill -> skill.config().matchesPrefixes(activePrefixes))
                .toList();
    }

    private static String loadSkill(String skillId, String skillPath, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(skillPath);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Cross-workflow skill prompt not found for '" + skillId + "' at: " + skillPath);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read cross-workflow skill prompt for '" + skillId + "' from: " + skillPath,
                    exception);
        }
    }

    public record LoadedCrossWorkflowSkill(CrossWorkflowSkillConfig config, String skillText) {
    }
}
