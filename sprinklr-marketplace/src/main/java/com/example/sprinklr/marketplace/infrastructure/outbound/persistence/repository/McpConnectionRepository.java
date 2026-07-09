package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.McpConnectionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface McpConnectionRepository extends MongoRepository<McpConnectionDocument, String> {

    List<McpConnectionDocument> findByUserId(String userId);

    Optional<McpConnectionDocument> findByIdAndUserId(String id, String userId);

    Optional<McpConnectionDocument> findByUserIdAndCatalogServerId(String userId, String catalogServerId);

    Optional<McpConnectionDocument> findByUserIdAndServerIdPrefix(String userId, String serverIdPrefix);

    void deleteByIdAndUserId(String id, String userId);
}
