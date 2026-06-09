package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Objects;

@Document(collection = "users")
public record User(
        @Id String id,
        String username,
        @Indexed(unique = true) String email,
        String password,
        boolean isVerified,
        LocalDateTime createdAt
) {

    public User(String id, String username, String email, String password, LocalDateTime createdAt) {
        this(id, username, email, password, false, createdAt);
    }

    public User {
        Objects.requireNonNull(username, "username must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(email, "email must not be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        Objects.requireNonNull(password, "password must not be null");
        if (password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
