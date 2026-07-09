package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.auth;

public record LoginResponse(String token, String userId, String email, String username) {}
