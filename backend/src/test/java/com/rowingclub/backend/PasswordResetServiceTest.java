package com.rowingclub.backend;

import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.PasswordResetTokenRepository;
import com.rowingclub.backend.repository.UserRepository;
import com.rowingclub.backend.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.rowingclub.backend.entity.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetServiceTest {

    @SpyBean private PasswordResetService service;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setup() {
        user = userRepository.save(User.builder()
                .fullName("Reset User")
                .email("reset_user@test.com")
                .passwordHash(passwordEncoder.encode("oldpass12"))
                .role(Role.MEMBER)
                .build());
    }

    @Test
    void unknownEmailSilentlySucceeds() {
        // Must not throw and must not create a token
        service.requestReset("nobody@nowhere.com");
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void fullRoundTripChangesPassword() {
        AtomicReference<String> capturedToken = new AtomicReference<>();
        doAnswer(invocation -> {
            // Extract the raw token the service generated
            String rawToken = invocation.getArgument(1);
            capturedToken.set(rawToken);
            return null;
        }).when(service).sendResetEmail(any(), any());

        service.requestReset(user.getEmail());
        assertThat(capturedToken.get()).isNotBlank();
        assertThat(tokenRepository.count()).isEqualTo(1);

        service.resetPassword(capturedToken.get(), "brandnewpass1");

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("brandnewpass1", updated.getPasswordHash())).isTrue();
        assertThat(updated.getRefreshToken()).isNull();
    }

    @Test
    void invalidTokenRejected() {
        assertThatThrownBy(() -> service.resetPassword("not-a-real-token", "brandnew12"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void usedTokenCannotBeReused() {
        AtomicReference<String> token = new AtomicReference<>();
        doAnswer(invocation -> { token.set(invocation.getArgument(1)); return null; })
                .when(service).sendResetEmail(any(), any());
        service.requestReset(user.getEmail());

        service.resetPassword(token.get(), "freshpass12");
        // Re-using the same token must fail (record marked used).
        assertThatThrownBy(() -> service.resetPassword(token.get(), "anotherpass12"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void expiredTokenRejected() {
        AtomicReference<String> token = new AtomicReference<>();
        doAnswer(invocation -> { token.set(invocation.getArgument(1)); return null; })
                .when(service).sendResetEmail(any(), any());
        service.requestReset(user.getEmail());

        // Force the stored token to be expired.
        PasswordResetToken record = tokenRepository.findAll().get(0);
        record.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(record);

        assertThatThrownBy(() -> service.resetPassword(token.get(), "freshpass12"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void secondRequestInvalidatesFirstToken() {
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();
        doAnswer(invocation -> { first.set(invocation.getArgument(1)); return null; })
                .when(service).sendResetEmail(any(), any());
        service.requestReset(user.getEmail());

        doAnswer(invocation -> { second.set(invocation.getArgument(1)); return null; })
                .when(service).sendResetEmail(any(), any());
        service.requestReset(user.getEmail());

        // First token must now be invalid
        assertThatThrownBy(() -> service.resetPassword(first.get(), "pwnew1234"))
                .isInstanceOf(BusinessException.class);
        // Second still works
        service.resetPassword(second.get(), "pwnew1234");
    }
}
