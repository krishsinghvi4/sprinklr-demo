package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsagePeriodSummary;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsageSummary;
import com.example.sprinklr.marketplace.domain.port.outbound.LLM.LlmUsagePort;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.User;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private McpMarketplaceService marketplaceService;

    @Mock
    private LlmUsagePort llmUsagePort;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void getProfile_includesUsageSummary() {
        User user = new User(
                "user-1",
                "alice",
                "alice@example.com",
                "hash",
                true,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(marketplaceService.getMarketplaceForUser("user-1"))
                .thenReturn(new McpMarketplaceService.MarketplaceView(List.of(), List.of()));
        when(llmUsagePort.getSummaryForUser("user-1")).thenReturn(new LlmUsageSummary(
                new LlmUsagePeriodSummary(1000, 800, 200, new BigDecimal("0.05"), 10),
                new LlmUsagePeriodSummary(250, 200, 50, new BigDecimal("0.01"), 2)
        ));

        ProfileService.ProfileView profile = profileService.getProfile("user-1");

        assertThat(profile.usage().allTime().totalTokens()).isEqualTo(1000);
        assertThat(profile.usage().allTime().llmCallCount()).isEqualTo(10);
        assertThat(profile.usage().currentMonth().totalTokens()).isEqualTo(250);
        assertThat(profile.usage().currentMonth().estimatedCostUsd()).isEqualByComparingTo("0.01");
    }
}
