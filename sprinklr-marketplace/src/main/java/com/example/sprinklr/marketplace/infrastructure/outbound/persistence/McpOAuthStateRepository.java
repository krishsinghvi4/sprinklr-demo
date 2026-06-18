package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface McpOAuthStateRepository extends MongoRepository<McpOAuthStateDocument, String> {

    /** Removes all in-flight OAuth states for a user/server pair before starting a new flow. */
    List<McpOAuthStateDocument> findByUserIdAndCatalogServerId(String userId, String catalogServerId);

    void deleteByUserIdAndCatalogServerId(String userId, String catalogServerId);
}
