package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.UserMcpConfigDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserMcpConfigRepository extends ReactiveMongoRepository<UserMcpConfigDocument, String> {

    Flux<UserMcpConfigDocument> findByUserIdOrderByCreatedAtDesc(String userId);

    Mono<UserMcpConfigDocument> findByIdAndUserId(String id, String userId);

    Mono<UserMcpConfigDocument> findByUserIdAndServerIdPrefix(String userId, String serverIdPrefix);

    Mono<Void> deleteByIdAndUserId(String id, String userId);
}
