package com.example.sprinklr.marketplace.domain.model;

/**
 * Catalog-driven credential auth header configuration.
 */
public record McpCredentialAuthConfig(
        String tokenField,
        McpCredentialHeaderMode headerMode,
        String emailField,
        String customHeaderName
) {

    public McpCredentialAuthConfig {
        if (tokenField == null || tokenField.isBlank()) {
            throw new IllegalArgumentException("McpCredentialAuthConfig tokenField must not be blank");
        }
        if (headerMode == null) {
            throw new IllegalArgumentException("McpCredentialAuthConfig headerMode must not be null");
        }
        if (headerMode == McpCredentialHeaderMode.BASIC_EMAIL
                && (emailField == null || emailField.isBlank())) {
            throw new IllegalArgumentException("BASIC_EMAIL header mode requires emailField");
        }
        if (headerMode == McpCredentialHeaderMode.CUSTOM
                && (customHeaderName == null || customHeaderName.isBlank())) {
            throw new IllegalArgumentException("CUSTOM header mode requires customHeaderName");
        }
    }
}
