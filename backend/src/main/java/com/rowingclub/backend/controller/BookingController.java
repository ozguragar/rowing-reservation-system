package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.BookingDto;
import com.rowingclub.backend.dto.BookingRequest;
import com.rowingclub.backend.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingDto> bookSeat(Authentication auth, @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.bookSeat(auth.getName(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BookingDto> cancelBooking(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(auth.getName(), id));
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingDto>> getMyBookings(Authentication auth) {
        return ResponseEntity.ok(bookingService.getUserBookings(auth.getName()));
    }

    @GetMapping("/boat/{boatId}")
    public ResponseEntity<?> getBoatBookings(@PathVariable Long boatId) {
        if (bookingService.isShowBookedMembers()) {
            return ResponseEntity.ok(bookingService.getBookingsForBoat(boatId));
        }
        return ResponseEntity.ok(Map.of("message", "Member visibility is disabled by admin"));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<BookingDto>> getSessionBookings(@PathVariable Long sessionId) {
        return ResponseEntity.ok(bookingService.getBookingsForSession(sessionId));
    }
}
