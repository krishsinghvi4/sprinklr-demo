package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.DashboardConversationDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DashboardConversationRepository extends ReactiveMongoRepository<DashboardConversationDocument, String> {

    Flux<DashboardConversationDocument> findByUserIdOrderByUpdatedAtDesc(String userId);

    Mono<DashboardConversationDocument> findByIdAndUserId(String id, String userId);

    Mono<DashboardConversationDocument> findByUserIdAndSourceConversationId(String userId, String sourceConversationId);

    Mono<Void> deleteByIdAndUserId(String id, String userId);
}
