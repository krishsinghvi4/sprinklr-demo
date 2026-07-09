package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "dashboard_turns")
@CompoundIndex(name = "dashboardConversationId_createdAt_idx", def = "{'dashboardConversationId': 1, 'createdAt': 1}")
public record DashboardTurnDocument(
        @Id String id,
        String dashboardConversationId,
        String userId,
        String sourceChatMessageId,
        String prompt,
        String narrative,
        String assistantContent,
        List<WidgetSpecDocument> widgets,
        String toolResultSnapshot,
        Map<String, String> extendedInsights,
        int version,
        String previousVersionId,
        Instant createdAt
) {
    public record WidgetSpecDocument(
            String id,
            String type,
            String title,
            String description,
            Map<String, Object> data
    ) {}
}
