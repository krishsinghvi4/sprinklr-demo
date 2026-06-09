package com.example.sprinklr.marketplace.infrastructure.config.security;

import java.util.regex.Pattern;

public final class MicrosoftEmailValidator {

    private static final Pattern MICROSOFT_EMAIL = Pattern.compile(
            ".+@(outlook\\.com|hotmail\\.com|live\\.com|sprinklr\\.com|([a-zA-Z0-9-]+\\.)*onmicrosoft\\.com)$",
            Pattern.CASE_INSENSITIVE
    );

    private MicrosoftEmailValidator() {
    }

    public static boolean isValid(String email) {
        return email != null && MICROSOFT_EMAIL.matcher(email).matches();
    }
}
