package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.LlmUsageEvent;
import com.example.sprinklr.marketplace.domain.model.LlmUsageSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoLlmUsageAdapterTest {

    @Mock
    private LlmUsageEventRepository repository;

    private MongoLlmUsageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MongoLlmUsageAdapter(repository);
    }

    @Test
    void record_persistsUsageEvent() {
        when(repository.save(any())).thenAnswer(invocation -> reactor.core.publisher.Mono.just(invocation.getArgument(0)));

        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        adapter.record(new LlmUsageEvent(
                "user-1",
                "conv-1",
                "gpt-4.1",
                100,
                25,
                125,
                new BigDecimal("0.0004"),
                now
        ));

        ArgumentCaptor<LlmUsageEventDocument> captor = ArgumentCaptor.forClass(LlmUsageEventDocument.class);
        verify(repository).save(captor.capture());
        LlmUsageEventDocument saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo("user-1");
        assertThat(saved.conversationId()).isEqualTo("conv-1");
        assertThat(saved.totalTokens()).isEqualTo(125);
        assertThat(saved.estimatedCostUsd()).isEqualByComparingTo("0.0004");
    }

    @Test
    void getSummaryForUser_aggregatesAllTimeAndCurrentMonth() {
        Instant monthStart = YearMonth.now(ZoneOffset.UTC)
                .atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        LlmUsageEventDocument priorMonth = new LlmUsageEventDocument(
                "id-1",
                "user-1",
                "conv-a",
                "gpt-4.1",
                100,
                20,
                120,
                new BigDecimal("0.0003"),
                monthStart.minusSeconds(3600)
        );
        LlmUsageEventDocument currentMonth = new LlmUsageEventDocument(
                "id-2",
                "user-1",
                "conv-b",
                "gpt-4.1",
                200,
                50,
                250,
                new BigDecimal("0.0008"),
                monthStart.plusSeconds(3600)
        );

        when(repository.findByUserId("user-1")).thenReturn(Flux.fromIterable(List.of(priorMonth, currentMonth)));
        when(repository.findByUserIdAndCreatedAtGreaterThanEqual(eq("user-1"), any(Instant.class)))
                .thenReturn(Flux.just(currentMonth));

        LlmUsageSummary summary = adapter.getSummaryForUser("user-1");

        assertThat(summary.allTime().totalTokens()).isEqualTo(370);
        assertThat(summary.allTime().llmCallCount()).isEqualTo(2);
        assertThat(summary.allTime().estimatedCostUsd()).isEqualByComparingTo("0.0011");

        assertThat(summary.currentMonth().totalTokens()).isEqualTo(250);
        assertThat(summary.currentMonth().llmCallCount()).isEqualTo(1);
        assertThat(summary.currentMonth().estimatedCostUsd()).isEqualByComparingTo("0.0008");
    }
}
