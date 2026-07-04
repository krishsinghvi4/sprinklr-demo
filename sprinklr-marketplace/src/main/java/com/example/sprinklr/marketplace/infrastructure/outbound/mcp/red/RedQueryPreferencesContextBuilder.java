package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Loads live RED query preferences and builds allowlist context for prompt assembly.
 */
@Component
public class RedQueryPreferencesContextBuilder {

    private static final String RED_PREFIX = "red.";

    private final McpRegistryPort registryPort;
    private final RedQueryPreferencesPromptAugmenter augmenter;

    public RedQueryPreferencesContextBuilder(
            McpRegistryPort registryPort,
            RedQueryPreferencesPromptAugmenter augmenter
    ) {
        this.registryPort = registryPort;
        this.augmenter = augmenter;
    }

    public RedQueryAllowlistContext build(
            String userId,
            List<McpTool> activeTools,
            List<Message> history,
            String currentTurnUserMessageId
    ) {
        if (userId == null || userId.isBlank() || activeTools == null || activeTools.isEmpty()) {
            return RedQueryAllowlistContext.empty();
        }

        Optional<String> connectionId = findRedConnectionId(activeTools);
        if (connectionId.isEmpty()) {
            return RedQueryAllowlistContext.empty();
        }

        return registryPort.findRedQueryPreferences(userId, connectionId.get())
                .map(preferences -> augmenter.build(
                        activeTools,
                        preferences,
                        history,
                        currentTurnUserMessageId))
                .orElse(RedQueryAllowlistContext.empty());
    }

    private static Optional<String> findRedConnectionId(List<McpTool> activeTools) {
        for (McpTool tool : activeTools) {
            if (tool.name().startsWith(RED_PREFIX)) {
                return Optional.of(tool.serverId());
            }
        }
        return Optional.empty();
    }
}
