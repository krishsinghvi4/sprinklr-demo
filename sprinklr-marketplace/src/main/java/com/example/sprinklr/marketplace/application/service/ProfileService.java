package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsagePeriodSummary;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsageSummary;
import com.example.sprinklr.marketplace.domain.port.outbound.LLM.LlmUsagePort;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.User;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final McpMarketplaceService marketplaceService;
    private final LlmUsagePort llmUsagePort;

    public ProfileService(
            UserRepository userRepository,
            McpMarketplaceService marketplaceService,
            LlmUsagePort llmUsagePort
    ) {
        this.userRepository = userRepository;
        this.marketplaceService = marketplaceService;
        this.llmUsagePort = llmUsagePort;
    }

    public ProfileView getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        McpMarketplaceService.MarketplaceView marketplace = marketplaceService.getMarketplaceForUser(userId);
        LlmUsageSummary usage = llmUsagePort.getSummaryForUser(userId);

        return new ProfileView(
                new UserView(user.id(), user.username(), user.email(), user.createdAt()),
                marketplace,
                UsageView.from(usage)
        );
    }

    public record ProfileView(
            UserView user,
            McpMarketplaceService.MarketplaceView marketplace,
            UsageView usage
    ) {}

    public record UserView(
            String userId,
            String username,
            String email,
            java.time.LocalDateTime createdAt
    ) {}

    public record UsageView(
            UsagePeriodView allTime,
            UsagePeriodView currentMonth
    ) {
        static UsageView from(LlmUsageSummary summary) {
            return new UsageView(
                    UsagePeriodView.from(summary.allTime()),
                    UsagePeriodView.from(summary.currentMonth())
            );
        }
    }

    public record UsagePeriodView(
            long totalTokens,
            long promptTokens,
            long completionTokens,
            BigDecimal estimatedCostUsd,
            long llmCallCount
    ) {
        static UsagePeriodView from(LlmUsagePeriodSummary summary) {
            return new UsagePeriodView(
                    summary.totalTokens(),
                    summary.promptTokens(),
                    summary.completionTokens(),
                    summary.estimatedCostUsd(),
                    summary.llmCallCount()
            );
        }
    }
}
