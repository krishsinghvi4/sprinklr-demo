package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface McpDcrClientRepository extends MongoRepository<McpDcrClientDocument, String> {
    Optional<McpDcrClientDocument> findByRedirectUri(String redirectUri);
}
