package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

@Document(collection = "users")
public record User(
        @Id String id,
        @Indexed(unique = true) String email,
        String password,
        LocalDateTime createdAt
) {

    private static final Pattern MICROSOFT_EMAIL = Pattern.compile(
            ".+@(outlook\\.com|hotmail\\.com|live\\.com|msn\\.com|microsoft\\.com|office365\\.com|sprinklr\\.com|([a-zA-Z0-9-]+\\.)*onmicrosoft\\.com)$",
            Pattern.CASE_INSENSITIVE
    );

    public User {
        Objects.requireNonNull(email, "email must not be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (!MICROSOFT_EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email must be a valid Outlook/Microsoft email address");
        }
        Objects.requireNonNull(password, "password must not be null");
        if (password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
