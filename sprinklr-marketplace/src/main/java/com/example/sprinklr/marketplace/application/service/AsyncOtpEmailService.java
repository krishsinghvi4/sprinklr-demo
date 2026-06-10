package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpPurpose;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncOtpEmailService {

    private final EmailService emailService;

    public AsyncOtpEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("otpEmailExecutor")
    public void sendOtpEmailAsync(String email, String otp, OtpPurpose purpose) {
        emailService.sendOtpEmail(email, otp, purpose);
    }
}
