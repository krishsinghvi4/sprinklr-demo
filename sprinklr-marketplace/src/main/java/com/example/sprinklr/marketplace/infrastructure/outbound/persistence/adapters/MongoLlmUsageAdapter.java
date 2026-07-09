package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.adapters;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsageEvent;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsagePeriodSummary;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsageSummary;
import com.example.sprinklr.marketplace.domain.port.outbound.LLM.LlmUsagePort;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.LlmUsageEventDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.LlmUsageEventRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
public class MongoLlmUsageAdapter implements LlmUsagePort {

    private final LlmUsageEventRepository repository;

    public MongoLlmUsageAdapter(LlmUsageEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(LlmUsageEvent event) {
        LlmUsageEventDocument document = new LlmUsageEventDocument(
                UUID.randomUUID().toString(),
                event.userId(),
                event.conversationId(),
                event.model(),
                event.promptTokens(),
                event.completionTokens(),
                event.totalTokens(),
                event.estimatedCostUsd(),
                event.createdAt()
        );
        repository.save(document).block();
    }

    @Override
    public LlmUsageSummary getSummaryForUser(String userId) {
        List<LlmUsageEventDocument> allEvents = repository.findByUserId(userId)
                .collectList()
                .block();
        if (allEvents == null || allEvents.isEmpty()) {
            return new LlmUsageSummary(LlmUsagePeriodSummary.empty(), LlmUsagePeriodSummary.empty());
        }

        Instant monthStart = YearMonth.now(ZoneOffset.UTC)
                .atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        List<LlmUsageEventDocument> monthEvents = repository
                .findByUserIdAndCreatedAtGreaterThanEqual(userId, monthStart)
                .collectList()
                .block();

        return new LlmUsageSummary(
                aggregate(allEvents),
                aggregate(monthEvents != null ? monthEvents : List.of())
        );
    }

    private static LlmUsagePeriodSummary aggregate(List<LlmUsageEventDocument> events) {
        if (events.isEmpty()) {
            return LlmUsagePeriodSummary.empty();
        }
        long prompt = 0;
        long completion = 0;
        long total = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (LlmUsageEventDocument event : events) {
            prompt += event.promptTokens();
            completion += event.completionTokens();
            total += event.totalTokens();
            if (event.estimatedCostUsd() != null) {
                cost = cost.add(event.estimatedCostUsd());
            }
        }
        return new LlmUsagePeriodSummary(total, prompt, completion, cost, events.size());
    }
}
