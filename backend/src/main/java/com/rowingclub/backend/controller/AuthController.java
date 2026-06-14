package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.*;
import com.rowingclub.backend.security.AuthCookieService;
import com.rowingclub.backend.service.AuthService;
import com.rowingclub.backend.service.PasswordResetService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletResponse response) {
        AuthResponse body = authService.register(request);
        writeAuthCookies(response, body);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletResponse response) {
        AuthResponse body = authService.login(request);
        writeAuthCookies(response, body);
        return ResponseEntity.ok(body);
    }

    /**
     * Refresh the access token. Reads the refresh token from the httpOnly cookie
     * first; falls back to the JSON body for legacy/mobile clients.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) Map<String, String> body,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        String refreshToken = readRefreshCookie(request);
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        AuthResponse authResponse = authService.refresh(refreshToken);
        writeAuthCookies(response, authResponse);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    private void writeAuthCookies(HttpServletResponse response, AuthResponse body) {
        authCookieService.writeAccessCookie(response, body.getAccessToken());
        authCookieService.writeRefreshCookie(response, body.getRefreshToken());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (AuthCookieService.REFRESH_COOKIE.equals(c.getName())
                    && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        // Always 200 regardless of whether the email exists (prevents enumeration).
        return ResponseEntity.ok(Map.of(
                "message", "If that email is registered, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated. Please log in with your new password."));
    }
}
