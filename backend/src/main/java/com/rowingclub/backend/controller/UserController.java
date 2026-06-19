package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.BookingDto;
import com.rowingclub.backend.dto.ChangePasswordRequest;
import com.rowingclub.backend.dto.UserDto;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.service.BookingService;
import com.rowingclub.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BookingService bookingService;

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("SUPERADMIN") || a.equals("CLUB_ADMIN") || a.equals("TRAINER"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication auth) {
        return ResponseEntity.ok(userService.getUserByEmail(auth.getName()));
    }

    /**
     * Access gated to self-or-admin. Prevents one authenticated user from
     * harvesting arbitrary profile data (including credit balances).
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id, Authentication auth) {
        UserDto caller = userService.getUserByEmail(auth.getName());
        if (!isAdmin(auth) && !caller.getId().equals(id)) {
            throw new AccessDeniedException("You can only view your own profile");
        }
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * A user's full reservation history (past + future). Self-or-admin only,
     * matching the access rule on {@link #getUser}.
     */
    @GetMapping("/{id}/bookings")
    public ResponseEntity<List<BookingDto>> getUserBookings(@PathVariable Long id, Authentication auth) {
        UserDto caller = userService.getUserByEmail(auth.getName());
        if (!isAdmin(auth) && !caller.getId().equals(id)) {
            throw new AccessDeniedException("You can only view your own reservations");
        }
        return ResponseEntity.ok(bookingService.getBookingsForUser(id));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication auth, @Valid @RequestBody ChangePasswordRequest req) {
        if (req.getCurrentPassword().equals(req.getNewPassword())) {
            throw new BusinessException("New password must differ from current password");
        }
        userService.changePassword(auth.getName(), req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }
}
