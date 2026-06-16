package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface McpOAuthStateRepository extends MongoRepository<McpOAuthStateDocument, String> {
}
