package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.otp.OtpPurpose;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "otp_entries")
public record OtpEntry(
        @Id String id,
        String email,
        OtpPurpose purpose,
        String otp,
        @Indexed(expireAfter = "0s") Instant expiresAt
) {

    public OtpEntry {
        Objects.requireNonNull(email, "email must not be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(otp, "otp must not be null");
        if (otp.isBlank()) {
            throw new IllegalArgumentException("otp must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
