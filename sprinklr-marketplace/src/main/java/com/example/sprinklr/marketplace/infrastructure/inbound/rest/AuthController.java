package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.application.service.OtpService;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ForgotPasswordRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.LoginRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.LoginResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MessageResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.RegisterRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ResetPasswordRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.VerifyOtpRequest;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpPurpose;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.OtpRepository;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.User;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.UserRepository;
import com.example.sprinklr.marketplace.infrastructure.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String VERIFIED_MARKER = "VERIFIED";

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(
            UserRepository userRepository,
            OtpRepository otpRepository,
            OtpService otpService,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new MessageResponse("Email is already registered"));
        }
        if (userRepository.existsByUsername(request.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new MessageResponse("Username is already taken"));
        }

        User user = new User(
                null,
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                LocalDateTime.now()
        );
        user = userRepository.save(user);

        otpService.generateAndQueueOtp(request.email(), OtpPurpose.SIGNUP);

        return ResponseEntity.ok(new MessageResponse("OTP sent"));
    }

    @PostMapping("/verify-signup-otp")
    public ResponseEntity<MessageResponse> verifySignupOtp(@Valid @RequestBody VerifyOtpRequest request) {
        if (!otpService.verifyOtp(request.email(), request.otp(), OtpPurpose.SIGNUP)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired OTP"));
        }

        User user = userRepository.findByEmail(request.email())
                .orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired OTP"));
        }

        User verifiedUser = new User(
                user.id(),
                user.username(),
                user.email(),
                user.password(),
                true,
                user.createdAt()
        );
        userRepository.save(verifiedUser);

        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Invalid email or password"));
        }
        if (!user.isVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Please verify your email first"));
        }

        String token = jwtUtil.generateToken(user.id(), user.email());
        return ResponseEntity.ok(new LoginResponse(token, user.id(), user.email(), user.username()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user ->
                otpService.generateAndQueueOtp(request.email(), OtpPurpose.FORGOT_PASSWORD)
        );

        return ResponseEntity.ok(new MessageResponse("If this email is registered, an OTP has been sent"));
    }

    @PostMapping("/verify-forgot-otp")
    public ResponseEntity<MessageResponse> verifyForgotOtp(@Valid @RequestBody VerifyOtpRequest request) {
        if (!otpService.verifyOtp(request.email(), request.otp(), OtpPurpose.FORGOT_PASSWORD)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired OTP"));
        }

        otpRepository.deleteByEmailAndPurpose(request.email(), OtpPurpose.PASSWORD_RESET_VERIFIED);
        OtpEntry verifiedEntry = new OtpEntry(
                null,
                request.email(),
                OtpPurpose.PASSWORD_RESET_VERIFIED,
                VERIFIED_MARKER,
                Instant.now().plusSeconds(600)
        );
        otpRepository.save(verifiedEntry);

        return ResponseEntity.ok(new MessageResponse("OTP verified successfully"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        OtpEntry verifiedEntry = otpRepository
                .findByEmailAndPurpose(request.email(), OtpPurpose.PASSWORD_RESET_VERIFIED)
                .orElse(null);

        if (verifiedEntry == null
                || !VERIFIED_MARKER.equals(verifiedEntry.otp())
                || !verifiedEntry.expiresAt().isAfter(Instant.now())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Password reset verification expired or not found"));
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Password reset verification expired or not found"));
        }

        User updatedUser = new User(
                user.id(),
                user.username(),
                user.email(),
                passwordEncoder.encode(request.newPassword()),
                user.isVerified(),
                user.createdAt()
        );
        userRepository.save(updatedUser);
        otpRepository.delete(verifiedEntry);

        return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
    }
}
