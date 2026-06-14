package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.SessionDto;
import com.rowingclub.backend.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping("/upcoming")
    public ResponseEntity<List<SessionDto>> getUpcomingSessions(Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        return ResponseEntity.ok(sessionService.getApprovedUpcomingSessions(clubId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getSessionWithBoats(id));
    }

    @GetMapping("/range")
    public ResponseEntity<List<SessionDto>> getSessionsByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        return ResponseEntity.ok(sessionService.getAllSessionsByDateRange(clubId, start, end));
    }
}
