package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.*;
import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.security.AuthCookieService;
import com.rowingclub.backend.security.JwtService;
import com.rowingclub.backend.service.ClubService;
import com.rowingclub.backend.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperadminController {

    private final ClubService clubService;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthCookieService authCookieService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> superadminLogin(
            @Valid @RequestBody SuperadminLoginRequest request,
            HttpServletResponse response) {
        
        User user = userService.findByEmail(request.getEmail());
        
        if (user.getRole() != Role.SUPERADMIN) {
            return ResponseEntity.status(403).build();
        }

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        
        userService.saveRefreshToken(user.getId(), refreshToken);
        authCookieService.writeAccessCookie(response, accessToken);
        authCookieService.writeRefreshCookie(response, refreshToken);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(UserDto.from(user))
                .build());
    }

    @GetMapping("/clubs")
    @PreAuthorize("hasAuthority('SUPERADMIN')")
    public ResponseEntity<List<ClubDto>> getAllClubs() {
        List<Club> clubs = clubService.getAllClubs();
        List<ClubDto> clubDtos = clubs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(clubDtos);
    }

    @GetMapping("/clubs/{clubId}")
    @PreAuthorize("hasAuthority('SUPERADMIN')")
    public ResponseEntity<ClubDto> getClub(@PathVariable Long clubId) {
        Club club = clubService.getClubById(clubId);
        return ResponseEntity.ok(toDto(club));
    }

    @PutMapping("/clubs/{clubId}/features")
    @PreAuthorize("hasAuthority('SUPERADMIN')")
    public ResponseEntity<ClubDto> updateClubFeatures(
            @PathVariable Long clubId,
            @RequestBody UpdateClubFeaturesRequest request) {
        
        Club club = clubService.updateClubFeatures(
                clubId,
                request.getFeatureAvailabilityModule(),
                request.getFeatureCancellationRequests(),
                request.getFeatureAutoScheduler(),
                request.getFeatureShowBookedMembers()
        );
        
        return ResponseEntity.ok(toDto(club));
    }

    @PostMapping("/impersonate")
    @PreAuthorize("hasAuthority('SUPERADMIN')")
    public ResponseEntity<AuthResponse> impersonateUser(
            @RequestParam String email,
            HttpServletResponse response) {
        
        User targetUser = userService.findByEmail(email);
        
        String accessToken = jwtService.generateAccessToken(targetUser.getEmail(), targetUser.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(targetUser.getEmail());
        
        userService.saveRefreshToken(targetUser.getId(), refreshToken);
        authCookieService.writeAccessCookie(response, accessToken);
        authCookieService.writeRefreshCookie(response, refreshToken);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(UserDto.from(targetUser))
                .build());
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasAuthority('SUPERADMIN')")
    public ResponseEntity<java.util.Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(java.util.Map.of("message", "Password changed successfully"));
    }

    private ClubDto toDto(Club club) {
        return ClubDto.builder()
                .id(club.getId())
                .name(club.getName())
                .createdAt(club.getCreatedAt())
                .featureAvailabilityModule(club.getFeatureAvailabilityModule())
                .featureCancellationRequests(club.getFeatureCancellationRequests())
                .featureAutoScheduler(club.getFeatureAutoScheduler())
                .featureShowBookedMembers(club.getFeatureShowBookedMembers())
                .build();
    }
}
