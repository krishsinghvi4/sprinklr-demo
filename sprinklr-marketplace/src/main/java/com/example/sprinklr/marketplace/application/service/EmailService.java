package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final boolean consoleOtpFallback;
    private final ExecutorService mailExecutor = Executors.newCachedThreadPool();

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromEmail,
            @Value("${app.mail.console-otp-fallback:false}") boolean consoleOtpFallback
    ) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.consoleOtpFallback = consoleOtpFallback;
    }

    public void sendOtpEmail(String toEmail, String otp, OtpPurpose purpose) {
        if (fromEmail == null || fromEmail.isBlank()) {
            if (consoleOtpFallback) {
                logOtpFallback(toEmail, otp, purpose, "MAIL_USERNAME not configured");
                return;
            }
            throw new IllegalStateException("Mail username not configured");
        }

        if (consoleOtpFallback) {
            logOtpFallback(toEmail, otp, purpose, "dev console fallback enabled");
            CompletableFuture.runAsync(() -> attemptSmtpSend(toEmail, otp, purpose), mailExecutor);
            return;
        }

        attemptSmtpSend(toEmail, otp, purpose);
    }

    private void attemptSmtpSend(String toEmail, String otp, OtpPurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail.trim());
        message.setTo(toEmail);
        message.setSubject(subjectFor(purpose));
        message.setText(bodyFor(otp, purpose));
        try {
            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (Exception exception) {
            if (consoleOtpFallback) {
                logOtpFallback(toEmail, otp, purpose, exception.getClass().getSimpleName());
                return;
            }
            throw exception;
        }
    }

    private void logOtpFallback(String toEmail, String otp, OtpPurpose purpose, String reason) {
        log.warn(
                "SMTP unavailable ({}). Console OTP fallback — purpose={}, recipient={}, otp={}",
                reason,
                purpose,
                toEmail,
                otp
        );
    }

    private String subjectFor(OtpPurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "Verify your Sprinklr Marketplace account";
            case FORGOT_PASSWORD -> "Reset your Sprinklr Marketplace password";
            case PASSWORD_RESET_VERIFIED -> "Password reset verified";
        };
    }

    private String bodyFor(String otp, OtpPurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "Your signup verification code is: " + otp + "\n\nThis code expires in 5 minutes.";
            case FORGOT_PASSWORD -> "Your password reset code is: " + otp + "\n\nThis code expires in 5 minutes.";
            case PASSWORD_RESET_VERIFIED -> "Your password reset has been verified.";
        };
    }
}
