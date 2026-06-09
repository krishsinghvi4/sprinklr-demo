package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

public record LoginResponse(String token, String userId, String email, String username) {}
