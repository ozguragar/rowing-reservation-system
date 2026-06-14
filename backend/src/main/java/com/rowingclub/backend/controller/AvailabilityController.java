package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.SessionDto;
import com.rowingclub.backend.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> setAvailability(Authentication auth, @PathVariable Long sessionId) {
        availabilityService.setAvailability(auth.getName(), sessionId);
        return ResponseEntity.ok(Map.of("message", "Availability saved"));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> removeAvailability(Authentication auth, @PathVariable Long sessionId) {
        availabilityService.removeAvailability(auth.getName(), sessionId);
        return ResponseEntity.ok(Map.of("message", "Availability removed"));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Long>> getMyAvailability(Authentication auth) {
        return ResponseEntity.ok(availabilityService.getUserAvailableSessions(auth.getName()));
    }

    @GetMapping("/week")
    public ResponseEntity<List<SessionDto>> getWeekSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ResponseEntity.ok(availabilityService.getWeekSessions(weekStart));
    }
}
