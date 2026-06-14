package com.rowingclub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight health + info endpoints for load balancers / uptime monitors.
 * Lives under /api/health — exempt from auth via SecurityConfig.
 *
 * - /api/health/live  — liveness: app is running. Cheap.
 * - /api/health/ready — readiness: app can serve traffic (DB reachable). Used by orchestrators.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean dbOk = result != null && result == 1;
            if (dbOk) return ResponseEntity.ok(Map.of("status", "UP", "db", "UP"));
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "db", "UNKNOWN"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "db", "DOWN"));
        }
    }
}
