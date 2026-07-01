package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DashboardTurnRepository extends ReactiveMongoRepository<DashboardTurnDocument, String> {

    Flux<DashboardTurnDocument> findByDashboardConversationIdAndUserIdOrderByCreatedAtAsc(
            String dashboardConversationId,
            String userId
    );

    Mono<DashboardTurnDocument> findByIdAndUserId(String id, String userId);

    Mono<Void> deleteByIdAndUserId(String id, String userId);

    Mono<Void> deleteByDashboardConversationIdAndUserId(String dashboardConversationId, String userId);
}
