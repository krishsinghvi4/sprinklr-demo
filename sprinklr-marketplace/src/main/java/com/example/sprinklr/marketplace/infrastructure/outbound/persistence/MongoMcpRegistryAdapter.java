package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mongo-backed registry that persists MCP connections and their discovered tools.
 * Acts as the source of truth for which tools are available per user.
 */
@Component
public class MongoMcpRegistryAdapter implements McpRegistryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoMcpRegistryAdapter.class);

    private final McpConnectionRepository repository;

    public MongoMcpRegistryAdapter(McpConnectionRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves or updates a user connection and its tool list.
     */
    @Override
    public McpUserConnection saveConnection(McpUserConnection connection, String encryptedCredentials, String serverIdPrefix) {
        String resolvedCredentials = encryptedCredentials;
        Optional<McpConnectionDocument> existing =
                connection.id() != null ? repository.findById(connection.id()) : Optional.empty();
        if (resolvedCredentials == null) {
            resolvedCredentials = existing.map(McpConnectionDocument::encryptedCredentials).orElse(null);
        }

        String resolvedPrefix = serverIdPrefix != null && !serverIdPrefix.isBlank()
                ? serverIdPrefix
                : resolveServerIdPrefix(connection);

        // Preserve any previously generated graph when re-saving the same connection id; a brand-new
        // connection starts PENDING and gets its graph filled in by updateDependencyGraph() at connect.
        ToolDependencyGraphDocument graphDocument =
                existing.map(McpConnectionDocument::toolDependencyGraph).orElse(null);
        String graphStatus = existing
                .map(McpConnectionDocument::dependencyGraphStatus)
                .orElse(DependencyGraphStatus.PENDING.name());

        McpConnectionDocument document = new McpConnectionDocument(
                connection.id(),
                connection.userId(),
                connection.catalogServerId(),
                resolvedPrefix,
                resolvedCredentials,
                connection.mcpSessionId(),
                connection.mcpProtocolVersion(),
                connection.status().name(),
                toToolDocuments(connection.tools()),
                connection.connectedAt(),
                connection.lastError(),
                graphDocument,
                graphStatus
        );

        McpConnectionDocument saved = repository.save(document);
        return toDomain(saved);
    }

    @Override
    public List<McpUserConnection> findByUserId(String userId) {
        return repository.findByUserId(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<McpUserConnection> findByIdAndUserId(String connectionId, String userId) {
        return repository.findByIdAndUserId(connectionId, userId).map(this::toDomain);
    }

    @Override
    public Optional<McpUserConnection> findByUserIdAndCatalogServerId(String userId, String catalogServerId) {
        return repository.findByUserIdAndCatalogServerId(userId, catalogServerId).map(this::toDomain);
    }

    @Override
    public Optional<McpUserConnection> findByUserIdAndServerIdPrefix(String userId, String serverIdPrefix) {
        return repository.findByUserIdAndServerIdPrefix(userId, serverIdPrefix).map(this::toDomain);
    }

    @Override
    public void updateSession(String connectionId, String sessionId, String protocolVersion) {
        repository.findById(connectionId).ifPresent(existing -> {
            McpConnectionDocument updated = new McpConnectionDocument(
                    existing.id(),
                    existing.userId(),
                    existing.catalogServerId(),
                    existing.serverIdPrefix(),
                    existing.encryptedCredentials(),
                    sessionId,
                    protocolVersion,
                    existing.status(),
                    existing.tools(),
                    existing.connectedAt(),
                    existing.lastError(),
                    existing.toolDependencyGraph(),
                    existing.dependencyGraphStatus()
            );
            repository.save(updated);
        });
    }

    @Override
    public void clearSession(String connectionId) {
        updateSession(connectionId, null, null);
    }

    @Override
    public void delete(String connectionId, String userId) {
        repository.deleteByIdAndUserId(connectionId, userId);
    }

    /**
     * Returns tools from all CONNECTED servers for tool whitelisting.
     */
    @Override
    public List<McpTool> findActiveToolsForUser(String userId) {
        return repository.findByUserId(userId).stream()
                .filter(doc -> McpConnectionStatus.CONNECTED.name().equals(doc.status()))
                .flatMap(doc -> doc.tools().stream())
                .map(this::toTool)
                .toList();
    }

    public Optional<McpConnectionDocument> findDocumentByIdAndUserId(String connectionId, String userId) {
        return repository.findByIdAndUserId(connectionId, userId);
    }

    @Override
    public void updateDependencyGraph(String connectionId, ToolDependencyGraph graph) {
        repository.findById(connectionId).ifPresentOrElse(existing -> {
            ToolDependencyGraphDocument graphDocument = new ToolDependencyGraphDocument(
                    toEdgeDocuments(graph.edges()),
                    graph.toolsFingerprint(),
                    graph.generatedAt()
            );
            McpConnectionDocument updated = new McpConnectionDocument(
                    existing.id(),
                    existing.userId(),
                    existing.catalogServerId(),
                    existing.serverIdPrefix(),
                    existing.encryptedCredentials(),
                    existing.mcpSessionId(),
                    existing.mcpProtocolVersion(),
                    existing.status(),
                    existing.tools(),
                    existing.connectedAt(),
                    existing.lastError(),
                    graphDocument,
                    graph.status().name()
            );
            repository.save(updated);
            log.info("[McpRegistry] Stored dependency graph connectionId={} prefix={} status={} edges={}",
                    connectionId, graph.serverIdPrefix(), graph.status(), graph.edges().size());
        }, () -> log.warn("[McpRegistry] updateDependencyGraph skipped — connectionId={} not found", connectionId));
    }

    @Override
    public List<ToolDependencyGraph> findActiveDependencyGraphsForUser(String userId) {
        return repository.findByUserId(userId).stream()
                .filter(doc -> McpConnectionStatus.CONNECTED.name().equals(doc.status()))
                .map(this::toDependencyGraph)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<ToolDependencyGraph> findDependencyGraphByConnectionId(String connectionId) {
        return repository.findById(connectionId).flatMap(this::toDependencyGraph);
    }

    private Optional<ToolDependencyGraph> toDependencyGraph(McpConnectionDocument document) {
        ToolDependencyGraphDocument graphDocument = document.toolDependencyGraph();
        if (graphDocument == null) {
            return Optional.empty();
        }
        DependencyGraphStatus status = parseGraphStatus(document.dependencyGraphStatus());
        return Optional.of(new ToolDependencyGraph(
                document.serverIdPrefix(),
                toEdgeMap(graphDocument.edges()),
                graphDocument.toolsFingerprint(),
                graphDocument.generatedAt(),
                status
        ));
    }

    private List<EdgeDocument> toEdgeDocuments(Map<String, List<String>> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        List<EdgeDocument> documents = new ArrayList<>();
        edges.forEach((tool, prerequisites) -> documents.add(new EdgeDocument(tool, prerequisites)));
        return documents;
    }

    private Map<String, List<String>> toEdgeMap(List<EdgeDocument> edges) {
        if (edges == null || edges.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (EdgeDocument edge : edges) {
            if (edge.tool() != null && !edge.tool().isBlank()) {
                map.put(edge.tool(), edge.prerequisites() == null ? List.of() : edge.prerequisites());
            }
        }
        return map;
    }

    private DependencyGraphStatus parseGraphStatus(String status) {
        if (status == null || status.isBlank()) {
            return DependencyGraphStatus.PENDING;
        }
        try {
            return DependencyGraphStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            log.warn("[McpRegistry] Unknown dependencyGraphStatus '{}' — treating as FAILED", status);
            return DependencyGraphStatus.FAILED;
        }
    }

    private String resolveServerIdPrefix(McpUserConnection connection) {
        if (!connection.tools().isEmpty()) {
            String name = connection.tools().get(0).name();
            int dot = name.indexOf('.');
            if (dot > 0) {
                return name.substring(0, dot);
            }
        }
        return connection.catalogServerId();
    }

    private List<McpToolDocument> toToolDocuments(List<McpTool> tools) {
        return tools.stream()
                .map(tool -> new McpToolDocument(
                        tool.name(),
                        tool.description(),
                        tool.serverId(),
                        tool.inputSchemaJson()
                ))
                .toList();
    }

    private McpUserConnection toDomain(McpConnectionDocument document) {
        List<McpTool> tools = document.tools() == null
                ? List.of()
                : document.tools().stream().map(this::toTool).toList();

        return new McpUserConnection(
                document.id(),
                document.userId(),
                document.catalogServerId(),
                McpConnectionStatus.valueOf(document.status()),
                document.mcpSessionId(),
                document.mcpProtocolVersion(),
                tools,
                document.connectedAt(),
                document.lastError()
        );
    }

    private McpTool toTool(McpToolDocument document) {
        return new McpTool(
                document.name(),
                document.description(),
                document.serverId(),
                document.inputSchemaJson()
        );
    }
}
