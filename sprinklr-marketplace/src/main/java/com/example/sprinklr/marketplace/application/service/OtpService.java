package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.OtpEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.otp.OtpPurpose;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.OtpRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    static final int OTP_TTL_SECONDS = 120;

    private final OtpRepository otpRepository;
    private final AsyncOtpEmailService asyncOtpEmailService;

    public OtpService(OtpRepository otpRepository, AsyncOtpEmailService asyncOtpEmailService) {
        this.otpRepository = otpRepository;
        this.asyncOtpEmailService = asyncOtpEmailService;
    }

    public String generateOtp(String email, OtpPurpose purpose) {
        otpRepository.deleteByEmailAndPurpose(email, purpose);

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpEntry entry = new OtpEntry(null, email, purpose, otp, Instant.now().plusSeconds(OTP_TTL_SECONDS));
        otpRepository.save(entry);

        return otp;
    }

    public void queueOtpEmail(String email, String otp, OtpPurpose purpose) {
        asyncOtpEmailService.sendOtpEmailAsync(email, otp, purpose);
    }

    public void generateAndQueueOtp(String email, OtpPurpose purpose) {
        String otp = generateOtp(email, purpose);
        queueOtpEmail(email, otp, purpose);
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
