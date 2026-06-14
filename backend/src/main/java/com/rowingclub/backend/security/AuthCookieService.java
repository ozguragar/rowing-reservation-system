package com.rowingclub.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Writes and clears the httpOnly auth cookies.
 *
 * <p>Cookies used:
 * <ul>
 *   <li>{@code access_token}  — carries the short-lived JWT. Lifetime = 15 min.</li>
 *   <li>{@code refresh_token} — carries the rotating refresh JWT. Lifetime = 7 days.
 *       Path-scoped to {@code /api/auth/refresh} so it never rides along with normal requests.</li>
 * </ul>
 *
 * <p><b>SameSite=Lax</b> is the right default for a first-party SPA: it blocks
 * cookies from cross-site POST/PUT/DELETE requests (CSRF protection) while still
 * allowing top-level navigation. Combined with httpOnly (no JS read) and Secure
 * (HTTPS only in prod), this replaces localStorage token storage.
 *
 * <p>The {@code secure} flag is toggled by {@code app.auth.cookie-secure} so dev
 * over plain HTTP still works. Production MUST set this to true via env.
 */
@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE  = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_PATH  = "/api/auth";

    @Value("${app.auth.cookie-secure:false}")
    private boolean secure;

    @Value("${app.auth.cookie-same-site:Lax}")
    private String sameSite;

    @Value("${app.jwt.access-token-expiration:900000}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpirationMs;

    public void writeAccessCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(ACCESS_COOKIE, token)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite(sameSite)
                        .path("/")
                        .maxAge(accessTokenExpirationMs / 1000)
                        .build()
                        .toString());
    }

    public void writeRefreshCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(REFRESH_COOKIE, token)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite(sameSite)
                        .path(REFRESH_PATH)
                        .maxAge(refreshTokenExpirationMs / 1000)
                        .build()
                        .toString());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(ACCESS_COOKIE, "")
                        .httpOnly(true).secure(secure).sameSite(sameSite)
                        .path("/").maxAge(0).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(REFRESH_COOKIE, "")
                        .httpOnly(true).secure(secure).sameSite(sameSite)
                        .path(REFRESH_PATH).maxAge(0).build().toString());
    }
}
