package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.PendingWorkflowDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Blocking repository for {@link PendingWorkflowDocument}; keyed by conversationId.
 */
public interface PendingWorkflowRepository extends MongoRepository<PendingWorkflowDocument, String> {
}
