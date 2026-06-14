package com.rowingclub.backend.service;

import com.rowingclub.backend.entity.PasswordResetToken;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.PasswordResetTokenRepository;
import com.rowingclub.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Password reset flow:
 *
 *   1. Caller POSTs /api/auth/forgot-password with an email.
 *      Service ALWAYS returns 200 regardless of whether the address exists
 *      (prevents user enumeration). If the email is known, a fresh 32-byte
 *      random token is generated, only its SHA-256 hash is stored, and the
 *      reset link is emitted via {@link #sendResetEmail}.
 *   2. Caller POSTs /api/auth/reset-password with the raw token + new password.
 *      Service hashes the token, looks it up, checks expiry + unused, then
 *      updates the password hash and clears the refresh token to invalidate
 *      any existing sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRY_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @PostConstruct
    void warn() {
        log.info("PasswordResetService active; reset links will be emitted at {}/reset-password?token=...", frontendBaseUrl);
    }

    @Transactional
    public void requestReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Silent success — don't leak whether the email is registered.
            log.debug("forgot-password for unknown email (silent): {}", email);
            return;
        }
        tokenRepository.invalidateAllForUser(user.getId());

        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        PasswordResetToken record = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();
        tokenRepository.save(record);

        sendResetEmail(user, rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken record = tokenRepository.findByTokenHashAndUsedFalse(sha256(rawToken))
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token"));

        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Invalid or expired reset token");
        }

        User user = userRepository.findById(record.getUserId())
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setRefreshToken(null);   // invalidate all existing sessions
        userRepository.save(user);

        record.setUsed(true);
        tokenRepository.save(record);

        log.info("password reset completed for userId={}", user.getId());
    }

    /**
     * Stub email sender. Logs the reset link so an operator can relay it
     * manually during development, and so integration tests can assert the
     * flow. Swap for an SMTP/SES client in production.
     */
    public void sendResetEmail(User user, String rawToken) {
        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        log.info("PASSWORD RESET EMAIL — to={} link={} expiresInMinutes={}",
                user.getEmail(), link, EXPIRY_MINUTES);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
