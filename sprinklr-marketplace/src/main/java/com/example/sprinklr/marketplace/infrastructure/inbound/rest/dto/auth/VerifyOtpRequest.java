package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(
        @NotBlank @Email String email,
        @NotBlank String otp
) {}
