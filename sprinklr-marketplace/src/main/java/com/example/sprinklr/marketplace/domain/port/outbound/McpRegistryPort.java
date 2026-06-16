package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;

import java.util.List;
import java.util.Optional;

public interface McpRegistryPort {

    McpUserConnection saveConnection(McpUserConnection connection, String encryptedCredentials, String serverIdPrefix);

    List<McpUserConnection> findByUserId(String userId);

    Optional<McpUserConnection> findByIdAndUserId(String connectionId, String userId);

    Optional<McpUserConnection> findByUserIdAndCatalogServerId(String userId, String catalogServerId);

    Optional<McpUserConnection> findByUserIdAndServerIdPrefix(String userId, String serverIdPrefix);

    void updateSession(String connectionId, String sessionId, String protocolVersion);

    void clearSession(String connectionId);

    void delete(String connectionId, String userId);

    List<McpTool> findActiveToolsForUser(String userId);
}
