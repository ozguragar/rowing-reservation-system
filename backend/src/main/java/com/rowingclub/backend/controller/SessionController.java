package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.SessionDto;
import com.rowingclub.backend.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping("/upcoming")
    public ResponseEntity<List<SessionDto>> getUpcomingSessions() {
        return ResponseEntity.ok(sessionService.getApprovedUpcomingSessions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getSessionWithBoats(id));
    }

    @GetMapping("/range")
    public ResponseEntity<List<SessionDto>> getSessionsByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(sessionService.getApprovedUpcomingSessions().stream()
                .filter(s -> !s.getDate().isBefore(start) && !s.getDate().isAfter(end))
                .toList());
    }
}
