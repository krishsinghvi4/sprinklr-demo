package com.example.sprinklr.marketplace.infrastructure.inbound.rest.exceptionHandling;

import com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers.McpConnectionController;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers.McpOAuthController;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers.ProfileController;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Chat.MessageResponse;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpCircuitOpenException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpErrorCode;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpOAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@RestControllerAdvice(basePackageClasses = {
        ProfileController.class,
        McpConnectionController.class,
        McpOAuthController.class
})
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest()
                .body(new MessageResponse(message, McpErrorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new MessageResponse(exception.getMessage(), McpErrorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(McpConnectionException.class)
    public ResponseEntity<MessageResponse> handleMcpConnection(McpConnectionException exception) {
        log.warn("[API] MCP connection error: {}", exception.getMessage());
        String lowerMessage = exception.getUserMessage().toLowerCase();
        boolean credentialFailure = lowerMessage.contains("credential")
                || lowerMessage.contains("invalid")
                || lowerMessage.contains("token");
        HttpStatus status = credentialFailure
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.BAD_GATEWAY;
        McpErrorCode code = credentialFailure
                ? McpErrorCode.AUTH_ERROR
                : McpErrorCode.MCP_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(new MessageResponse(exception.getUserMessage(), code.name()));
    }

    @ExceptionHandler(McpDiscoveryException.class)
    public ResponseEntity<MessageResponse> handleMcpDiscovery(McpDiscoveryException exception) {
        log.warn("[API] MCP discovery error: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new MessageResponse(exception.getUserMessage(), McpErrorCode.MCP_UNAVAILABLE.name()));
    }

    @ExceptionHandler(McpOAuthException.class)
    public ResponseEntity<MessageResponse> handleMcpOAuth(McpOAuthException exception) {
        log.warn("[API] MCP OAuth error: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponse(exception.getUserMessage(), McpErrorCode.OAUTH_ERROR.name()));
    }

    @ExceptionHandler(McpCircuitOpenException.class)
    public ResponseEntity<MessageResponse> handleCircuitOpen(McpCircuitOpenException exception) {
        log.warn("[API] MCP circuit open: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new MessageResponse(exception.getUserMessage(), McpErrorCode.MCP_CIRCUIT_OPEN.name()));
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<MessageResponse> handleNetwork(WebClientRequestException exception) {
        log.warn("[API] Network error: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new MessageResponse(
                        "Unable to reach the MCP server. Check your network and try again.",
                        McpErrorCode.NETWORK_ERROR.name()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> handleUnexpected(Exception exception) {
        log.error("[API] Unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("Something went wrong", McpErrorCode.UNKNOWN_ERROR.name()));
    }
}
