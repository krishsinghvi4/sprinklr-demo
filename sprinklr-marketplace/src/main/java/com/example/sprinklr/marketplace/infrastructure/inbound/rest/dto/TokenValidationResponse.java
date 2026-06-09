package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

public record TokenValidationResponse(
        boolean valid,
        String userId,
        String email
) {
}
