package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.outbound.email.GraphMailClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.otp.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final GraphMailClient graphMailClient;
    private final String fromEmail;
    private final String mailProvider;
    private final boolean consoleOtpFallback;

    public EmailService(
            JavaMailSender mailSender,
            GraphMailClient graphMailClient,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${app.mail.provider:auto}") String mailProvider,
            @Value("${app.mail.console-otp-fallback:false}") boolean consoleOtpFallback
    ) {
        this.mailSender = mailSender;
        this.graphMailClient = graphMailClient;
        this.fromEmail = fromEmail;
        this.mailProvider = mailProvider == null ? "auto" : mailProvider.trim().toLowerCase();
        this.consoleOtpFallback = consoleOtpFallback;
    }

    public void sendOtpEmail(String toEmail, String otp, OtpPurpose purpose) {
        String subject = subjectFor(purpose);
        String body = bodyFor(otp, purpose);

        try {
            sendEmail(toEmail, subject, body);
            log.info("OTP email sent to {} via {}", toEmail, resolveProvider());
        } catch (Exception exception) {
            log.error("Failed to send OTP email to {}: {}", toEmail, rootMessage(exception), exception);
            if (consoleOtpFallback) {
                logOtpFallback(toEmail, otp, purpose, rootMessage(exception));
                return;
            }
            throw new IllegalStateException("Failed to send OTP email: " + rootMessage(exception), exception);
        }
    }

    private void sendEmail(String toEmail, String subject, String body) {
        String provider = resolveProvider();
        if ("graph".equals(provider)) {
            graphMailClient.sendEmail(toEmail, subject, body);
            return;
        }
        if ("smtp".equals(provider)) {
            sendViaSmtp(toEmail, subject, body);
            return;
        }
        throw new IllegalStateException("No mail provider configured");
    }

    private String resolveProvider() {
        if ("graph".equals(mailProvider)) {
            if (!graphMailClient.isConfigured()) {
                throw new IllegalStateException("MAIL_PROVIDER=graph but Azure Graph credentials are missing");
            }
            return "graph";
        }
        if ("smtp".equals(mailProvider)) {
            if (fromEmail == null || fromEmail.isBlank()) {
                throw new IllegalStateException("MAIL_PROVIDER=smtp but MAIL_USERNAME is not configured");
            }
            return "smtp";
        }

        if (graphMailClient.isConfigured()) {
            return "graph";
        }
        if (fromEmail != null && !fromEmail.isBlank()) {
            return "smtp";
        }
        throw new IllegalStateException("No mail provider configured");
    }

    private void sendViaSmtp(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail.trim());
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private void logOtpFallback(String toEmail, String otp, OtpPurpose purpose, String reason) {
        log.warn(
                "Mail delivery failed ({}). Console OTP fallback — purpose={}, recipient={}, otp={}",
                reason,
                purpose,
                toEmail,
                otp
        );
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        String message = current.getMessage();
        while (current.getCause() != null) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null ? exception.getClass().getSimpleName() : message;
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
            case SIGNUP -> "Your signup verification code is: " + otp + "\n\nThis code expires in 2 minutes.";
            case FORGOT_PASSWORD -> "Your password reset code is: " + otp + "\n\nThis code expires in 2 minutes.";
            case PASSWORD_RESET_VERIFIED -> "Your password reset has been verified.";
        };
    }
}
