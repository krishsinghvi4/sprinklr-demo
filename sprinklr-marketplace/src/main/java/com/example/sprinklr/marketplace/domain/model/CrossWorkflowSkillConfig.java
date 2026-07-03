package com.example.sprinklr.marketplace.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Cross-server workflow skill injected when all {@code requiredPrefixes} appear in scoped tools.
 */
public record CrossWorkflowSkillConfig(
        String id,
        String title,
        String skillPath,
        Set<String> requiredPrefixes
) {

    public CrossWorkflowSkillConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CrossWorkflowSkillConfig id must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("CrossWorkflowSkillConfig title must not be blank");
        }
        if (skillPath == null || skillPath.isBlank()) {
            throw new IllegalArgumentException("CrossWorkflowSkillConfig skillPath must not be blank");
        }
        if (requiredPrefixes == null || requiredPrefixes.isEmpty()) {
            throw new IllegalArgumentException("CrossWorkflowSkillConfig requiredPrefixes must not be empty");
        }
        requiredPrefixes = Set.copyOf(requiredPrefixes);
    }

    public boolean matchesPrefixes(Set<String> activePrefixes) {
        return activePrefixes.containsAll(requiredPrefixes);
    }
}
