package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.debug.DebugLog;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpPurpose;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpRepository otpRepository;
    private final EmailService emailService;

    public OtpService(OtpRepository otpRepository, EmailService emailService) {
        this.otpRepository = otpRepository;
        this.emailService = emailService;
    }

    public void generateAndSendOtp(String email, OtpPurpose purpose) {
        otpRepository.deleteByEmailAndPurpose(email, purpose);

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpEntry entry = new OtpEntry(null, email, purpose, otp, Instant.now().plusSeconds(300));
        otpRepository.save(entry);

        // #region agent log
        DebugLog.write("OtpService.java:generateAndSendOtp", "otp saved, calling email service", "H4", "{\"otpSaved\":true}");
        // #endregion

        emailService.sendOtpEmail(email, otp, purpose);
    }

    public boolean verifyOtp(String email, String otp, OtpPurpose purpose) {
        return otpRepository.findByEmailAndPurpose(email, purpose)
                .filter(entry -> entry.expiresAt().isAfter(Instant.now()))
                .filter(entry -> entry.otp().equals(otp))
                .map(entry -> {
                    otpRepository.delete(entry);
                    return true;
                })
                .orElse(false);
    }
}
