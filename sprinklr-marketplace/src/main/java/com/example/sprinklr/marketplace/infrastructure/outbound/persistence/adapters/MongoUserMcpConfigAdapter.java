package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.adapters;

import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectProbeConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialHeaderMode;
import com.example.sprinklr.marketplace.domain.model.MCP.UserMcpConfig;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.UserMcpConfigPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.UserMcpConfigDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.UserMcpConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MongoUserMcpConfigAdapter implements UserMcpConfigPort {

    private final UserMcpConfigRepository repository;

    public MongoUserMcpConfigAdapter(UserMcpConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserMcpConfig save(UserMcpConfig config) {
        UserMcpConfigDocument saved = repository.save(toDocument(config)).block();
        return toDomain(saved);
    }

    @Override
    public List<UserMcpConfig> findByUserId(String userId) {
        List<UserMcpConfigDocument> documents = repository.findByUserIdOrderByCreatedAtDesc(userId)
                .collectList()
                .block();
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<UserMcpConfig> findByIdAndUserId(String id, String userId) {
        UserMcpConfigDocument document = repository.findByIdAndUserId(id, userId).block();
        return document == null ? Optional.empty() : Optional.of(toDomain(document));
    }

    @Override
    public Optional<UserMcpConfig> findByUserIdAndServerIdPrefix(String userId, String serverIdPrefix) {
        UserMcpConfigDocument document = repository.findByUserIdAndServerIdPrefix(userId, serverIdPrefix).block();
        return document == null ? Optional.empty() : Optional.of(toDomain(document));
    }

    @Override
    public void deleteByIdAndUserId(String id, String userId) {
        repository.deleteByIdAndUserId(id, userId).block();
    }

    private UserMcpConfigDocument toDocument(UserMcpConfig config) {
        List<UserMcpConfigDocument.CredentialFieldDocument> fields = config.credentialFields().stream()
                .map(field -> new UserMcpConfigDocument.CredentialFieldDocument(
                        field.key(),
                        field.label(),
                        field.type(),
                        field.required()
                ))
                .toList();

        UserMcpConfigDocument.ConnectProbeDocument probe = null;
        if (config.connectProbe() != null) {
            probe = new UserMcpConfigDocument.ConnectProbeDocument(
                    config.connectProbe().tool(),
                    config.connectProbe().argumentsJson(),
                    config.connectProbe().failureMessage()
            );
        }

        return new UserMcpConfigDocument(
                config.id(),
                config.userId(),
                config.displayName(),
                config.description(),
                config.endpointUrl(),
                config.serverIdPrefix(),
                config.tokenField(),
                config.headerMode().name(),
                config.customHeaderName(),
                fields,
                probe,
                config.skillText(),
                config.createdAt()
        );
    }

    private UserMcpConfig toDomain(UserMcpConfigDocument document) {
        List<McpCredentialField> fields = document.credentialFields() == null
                ? List.of()
                : document.credentialFields().stream()
                .map(field -> new McpCredentialField(
                        field.key(),
                        field.label(),
                        field.type(),
                        field.required()
                ))
                .toList();

        McpConnectProbeConfig probe = null;
        if (document.connectProbe() != null) {
            probe = new McpConnectProbeConfig(
                    document.connectProbe().tool(),
                    document.connectProbe().argumentsJson(),
                    document.connectProbe().failureMessage()
            );
        }

        return new UserMcpConfig(
                document.id(),
                document.userId(),
                document.displayName(),
                document.description(),
                document.endpointUrl(),
                document.serverIdPrefix(),
                document.tokenField(),
                McpCredentialHeaderMode.valueOf(document.headerMode()),
                document.customHeaderName(),
                fields,
                probe,
                document.skillText(),
                document.createdAt()
        );
    }
}
