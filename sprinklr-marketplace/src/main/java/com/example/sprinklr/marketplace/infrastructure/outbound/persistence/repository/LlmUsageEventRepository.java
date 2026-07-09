package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.LlmUsageEventDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface LlmUsageEventRepository extends ReactiveMongoRepository<LlmUsageEventDocument, String> {

    Flux<LlmUsageEventDocument> findByUserId(String userId);

    Flux<LlmUsageEventDocument> findByUserIdAndCreatedAtGreaterThanEqual(String userId, Instant createdAt);
}
