package com.rowingclub.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Sliding-window rate limiter for authentication endpoints.
 * Protects /api/auth/login, /register, /refresh from brute force.
 *
 * - Default limit: 10 requests/minute/IP (attacker will hit 429 long before cracking a password).
 * - Client IP resolution respects X-Forwarded-For ONLY when the immediate client is in the
 *   configured trusted-proxy list (app.security.trusted-proxies). Otherwise the header is ignored
 *   to prevent header-spoofing bypass.
 * - Buckets older than 2× window are periodically evicted to cap memory under attack.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration EVICTION_AFTER = WINDOW.multipliedBy(2);
    private static final int MAX_BUCKETS = 100_000;  // Hard cap against runaway growth

    /** Requests per minute per IP. Tunable so tests (and high-traffic deploys) can raise it. */
    private final int maxRequests;
    private final Set<String> trustedProxies;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile Instant lastCleanup = Instant.now();

    public AuthRateLimitFilter(
            @Value("${app.security.trusted-proxies:}") String trustedProxiesCsv,
            @Value("${app.security.auth-rate-limit-per-minute:10}") int maxRequests) {
        this.maxRequests = maxRequests;
        this.trustedProxies = trustedProxiesCsv == null || trustedProxiesCsv.isBlank()
                ? Set.of()
                : java.util.Arrays.stream(trustedProxiesCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (!uri.startsWith("/api/auth/")) {
            chain.doFilter(req, res);
            return;
        }

        String clientIp = resolveClientIp(req);
        maybeCleanup();

        // Hard cap to stop unbounded growth under distributed attack
        if (buckets.size() >= MAX_BUCKETS && !buckets.containsKey(clientIp)) {
            res.setStatus(429);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write("{\"message\":\"Service temporarily busy. Try again shortly.\",\"status\":429}");
            return;
        }

        Bucket b = buckets.computeIfAbsent(clientIp, k -> new Bucket());
        if (!b.tryAcquire(maxRequests)) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(WINDOW.getSeconds()));
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write(
                "{\"message\":\"Too many authentication attempts. Try again in a minute.\",\"status\":429}");
            return;
        }
        chain.doFilter(req, res);
    }

    /**
     * Resolve the real client IP. X-Forwarded-For is only honored when the immediate
     * remoteAddr is in the configured trusted-proxy allowlist. Otherwise attackers could
     * forge arbitrary IPs to escape rate limiting.
     */
    private String resolveClientIp(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        if (trustedProxies.contains(remoteAddr)) {
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private void maybeCleanup() {
        Instant now = Instant.now();
        if (Duration.between(lastCleanup, now).compareTo(WINDOW) < 0) return;
        lastCleanup = now;
        buckets.entrySet().removeIf(e -> {
            Bucket b = e.getValue();
            synchronized (b) {
                return Duration.between(b.windowStart, now).compareTo(EVICTION_AFTER) >= 0;
            }
        });
    }

    private static class Bucket {
        private Instant windowStart = Instant.now();
        private int count = 0;

        synchronized boolean tryAcquire(int maxRequests) {
            Instant now = Instant.now();
            if (Duration.between(windowStart, now).compareTo(WINDOW) >= 0) {
                windowStart = now;
                count = 0;
            }
            if (count >= maxRequests) return false;
            count++;
            return true;
        }
    }
}
