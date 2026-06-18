package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface McpDcrClientRepository extends MongoRepository<McpDcrClientDocument, String> {

    Optional<McpDcrClientDocument> findByProviderKeyAndRedirectUri(String providerKey, String redirectUri);

    /** Legacy lookup — used during migration when providerKey was not stored. */
    Optional<McpDcrClientDocument> findByRedirectUri(String redirectUri);
}
