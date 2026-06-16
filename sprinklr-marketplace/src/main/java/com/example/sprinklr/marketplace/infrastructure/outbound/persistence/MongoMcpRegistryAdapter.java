package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MongoMcpRegistryAdapter implements McpRegistryPort {

    private final McpConnectionRepository repository;

    public MongoMcpRegistryAdapter(McpConnectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public McpUserConnection saveConnection(McpUserConnection connection, String encryptedCredentials, String serverIdPrefix) {
        String resolvedCredentials = encryptedCredentials;
        if (resolvedCredentials == null && connection.id() != null) {
            resolvedCredentials = repository.findById(connection.id())
                    .map(McpConnectionDocument::encryptedCredentials)
                    .orElse(null);
        }

        String resolvedPrefix = serverIdPrefix != null && !serverIdPrefix.isBlank()
                ? serverIdPrefix
                : resolveServerIdPrefix(connection);

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
                connection.lastError()
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
                    existing.lastError()
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
