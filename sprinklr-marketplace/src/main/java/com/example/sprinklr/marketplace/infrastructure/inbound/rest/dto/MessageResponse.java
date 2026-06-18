package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

/**
 * Standard API error/success payload with optional machine-readable error code for UI routing.
 */
public record MessageResponse(String message, String errorCode) {

    public MessageResponse(String message) {
        this(message, null);
    }
}
