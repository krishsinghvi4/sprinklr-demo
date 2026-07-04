package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;

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

    /** Stores (or replaces) the LLM-generated dependency graph for a single connection. */
    void updateDependencyGraph(String connectionId, ToolDependencyGraph graph);

    /** Dependency graphs for all of the user's CONNECTED servers (one per connection that has one). */
    List<ToolDependencyGraph> findActiveDependencyGraphsForUser(String userId);

    /** The dependency graph for a single connection, if one has been generated. */
    Optional<ToolDependencyGraph> findDependencyGraphByConnectionId(String connectionId);

    /** RED query allowlists for a connection, if configured. */
    Optional<RedQueryPreferences> findRedQueryPreferences(String userId, String connectionId);

    /** Replaces RED query allowlists for a connection (RED connections only). */
    void updateRedQueryPreferences(String userId, String connectionId, RedQueryPreferences preferences);
}
