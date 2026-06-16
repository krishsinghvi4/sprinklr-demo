package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.User;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final McpMarketplaceService marketplaceService;

    public ProfileService(UserRepository userRepository, McpMarketplaceService marketplaceService) {
        this.userRepository = userRepository;
        this.marketplaceService = marketplaceService;
    }

    public ProfileView getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        McpMarketplaceService.MarketplaceView marketplace = marketplaceService.getMarketplaceForUser(userId);

        return new ProfileView(
                new UserView(user.id(), user.username(), user.email(), user.createdAt()),
                marketplace
        );
    }

    public record ProfileView(
            UserView user,
            McpMarketplaceService.MarketplaceView marketplace
    ) {}

    public record UserView(
            String userId,
            String username,
            String email,
            java.time.LocalDateTime createdAt
    ) {}
}
