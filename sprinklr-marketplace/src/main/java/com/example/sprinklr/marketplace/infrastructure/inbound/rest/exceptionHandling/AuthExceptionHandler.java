package com.example.sprinklr.marketplace.infrastructure.inbound.rest.exceptionHandling;

import com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers.AuthController;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Chat.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> {
                    if ("email".equals(error.getField())) {
                        return "Invalid email format";
                    }
                    return error.getDefaultMessage();
                })
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new MessageResponse(message));
    }
}
