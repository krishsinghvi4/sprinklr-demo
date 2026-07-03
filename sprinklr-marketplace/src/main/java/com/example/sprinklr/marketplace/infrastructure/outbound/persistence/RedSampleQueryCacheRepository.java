package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Mongo repository for {@link RedSampleQueryCacheDocument}.
 */
public interface RedSampleQueryCacheRepository extends MongoRepository<RedSampleQueryCacheDocument, String> {
}
