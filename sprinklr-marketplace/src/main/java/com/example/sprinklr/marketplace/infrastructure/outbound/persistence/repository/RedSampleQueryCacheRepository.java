package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.RedSampleQueryCacheDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Mongo repository for {@link RedSampleQueryCacheDocument}.
 */
public interface RedSampleQueryCacheRepository extends MongoRepository<RedSampleQueryCacheDocument, String> {
}
