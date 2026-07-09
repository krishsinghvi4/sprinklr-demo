package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.MCP.McpUserConnection;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.UserMcpConfigPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Supplies inline skill text from per-user custom MCP configurations.
 */
@Component
public class UserMcpSkillProvider {

    private final McpRegistryPort registryPort;
    private final UserMcpConfigPort userMcpConfigPort;

    public UserMcpSkillProvider(McpRegistryPort registryPort, UserMcpConfigPort userMcpConfigPort) {
        this.registryPort = registryPort;
        this.userMcpConfigPort = userMcpConfigPort;
    }

    public Optional<String> findByServerIdPrefix(String userId, String serverIdPrefix) {
        if (userId == null || userId.isBlank() || serverIdPrefix == null || serverIdPrefix.isBlank()) {
            return Optional.empty();
        }
        return userMcpConfigPort.findByUserId(userId).stream()
                .filter(config -> serverIdPrefix.equals(config.serverIdPrefix()))
                .map(config -> config.skillText())
                .filter(skillText -> skillText != null && !skillText.isBlank())
                .findFirst();
    }

    public Map<String, String> findSkillsForActiveConnections(String userId, List<String> activePrefixes) {
        Map<String, String> skills = new LinkedHashMap<>();
        if (userId == null || userId.isBlank() || activePrefixes == null || activePrefixes.isEmpty()) {
            return skills;
        }

        List<McpUserConnection> connections = registryPort.findByUserId(userId);
        for (McpUserConnection connection : connections) {
            userMcpConfigPort.findByIdAndUserId(connection.catalogServerId(), userId)
                    .ifPresent(config -> {
                        if (config.skillText() != null
                                && !config.skillText().isBlank()
                                && activePrefixes.contains(config.serverIdPrefix())) {
                            skills.put(config.serverIdPrefix(), config.skillText().trim());
                        }
                    });
        }
        return skills;
    }
}
