package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import java.time.Instant;

public record DashboardTurnSummaryDto(
        String id,
        String prompt,
        int version,
        Instant createdAt
) {}
