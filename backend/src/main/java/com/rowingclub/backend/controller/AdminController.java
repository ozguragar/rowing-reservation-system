package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.*;
import com.rowingclub.backend.entity.AdminMessage;
import com.rowingclub.backend.entity.AppSetting;
import com.rowingclub.backend.entity.AuditLog;
import com.rowingclub.backend.repository.AdminMessageRepository;
import com.rowingclub.backend.repository.AppSettingRepository;
import com.rowingclub.backend.repository.AuditLogRepository;
import com.rowingclub.backend.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SessionService sessionService;
    private final BookingService bookingService;
    private final LedgerService ledgerService;
    private final UserService userService;
    private final AnalyticsService analyticsService;
    private final AutoSchedulerService autoSchedulerService;
    private final AuditLogRepository auditLogRepository;
    private final AdminMessageRepository adminMessageRepository;
    private final AppSettingRepository appSettingRepository;

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<SessionDto> createSession(@Valid @RequestBody CreateSessionRequest request, Authentication auth) {
        return ResponseEntity.ok(sessionService.createSession(request, sessionService.getClubForUser(auth.getName())));
    }

    @PostMapping("/sessions/bulk")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<List<SessionDto>> createBulkSessions(@Valid @RequestBody BulkSessionRequest request, Authentication auth) {
        return ResponseEntity.ok(sessionService.createBulkSessions(request.getSessions(), sessionService.getClubForUser(auth.getName())));
    }

    @PostMapping("/sessions/{id}/boats")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<BoatDto> addBoat(@PathVariable Long id, @Valid @RequestBody AddBoatRequest request) {
        return ResponseEntity.ok(sessionService.addBoatToSession(id, request));
    }

    @PatchMapping("/sessions/{id}/approve")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<SessionDto> approveSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.approveSession(id));
    }

    @PostMapping("/sessions/bulk-approve")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<List<SessionDto>> bulkApprove(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(sessionService.bulkApprove(ids));
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.ok(Map.of("message", "Session deleted"));
    }

    @PostMapping("/sessions/bulk-delete")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<Map<String, String>> bulkDeleteSessions(@RequestBody List<Long> ids) {
        sessionService.bulkDeleteSessions(ids);
        return ResponseEntity.ok(Map.of("message", "Sessions deleted"));
    }

    @DeleteMapping("/boats/{id}")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<Map<String, String>> deleteBoat(@PathVariable Long id) {
        sessionService.deleteBoat(id);
        return ResponseEntity.ok(Map.of("message", "Boat deleted"));
    }

    @PostMapping("/sessions/copy-day")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<List<SessionDto>> copyDay(@Valid @RequestBody CopyDayRequest request, Authentication auth) {
        return ResponseEntity.ok(sessionService.copyDaySessions(
                request.getSourceDate(), request.getTargetDate(),
                sessionService.getClubForUser(auth.getName())));
    }

    @PostMapping("/sessions/copy-week")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<List<SessionDto>> copyWeek(@Valid @RequestBody CopyWeekRequest request, Authentication auth) {
        return ResponseEntity.ok(sessionService.copyWeekSessions(
                request.getSourceWeekStart(), request.getTargetWeekStart(),
                sessionService.getClubForUser(auth.getName())));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<List<SessionDto>> getAllSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        return ResponseEntity.ok(sessionService.getAllSessionsByDateRange(clubId, start, end));
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<BookingDto> adminBook(@Valid @RequestBody AdminBookRequest request) {
        return ResponseEntity.ok(bookingService.adminBookUser(
                request.getUserId(), request.getBoatId(), request.getSessionId(), request.getIsCoxSeat()));
    }

    @DeleteMapping("/bookings/{id}")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<Map<String, String>> adminRemoveBooking(@PathVariable Long id) {
        bookingService.adminRemoveBooking(id);
        return ResponseEntity.ok(Map.of("message", "Booking removed and credit refunded"));
    }

    @PostMapping("/bookings/move")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<BookingDto> adminMoveUser(@Valid @RequestBody AdminMoveRequest request) {
        return ResponseEntity.ok(bookingService.adminMoveUser(
                request.getUserId(), request.getFromBoatId(), request.getToBoatId(), request.getIsCoxSeat()));
    }

    @GetMapping("/cancellation-requests")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<List<BookingDto>> getPendingCancellations(Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        return ResponseEntity.ok(bookingService.getPendingCancellations(clubId));
    }

    @PostMapping("/cancellation-requests/{id}/approve")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<BookingDto> approveCancellation(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.approveCancellation(id));
    }

    @PostMapping("/cancellation-requests/{id}/deny")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<BookingDto> denyCancellation(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.denyCancellation(id));
    }

    @GetMapping("/ledger/{userId}")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<LedgerDto>> getUserLedger(@PathVariable Long userId) {
        return ResponseEntity.ok(ledgerService.getUserLedger(userId));
    }

    @PostMapping("/ledger/{userId}/credit")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<LedgerDto> addCredit(
            @PathVariable Long userId, @Valid @RequestBody AddCreditRequest request) {
        return ResponseEntity.ok(ledgerService.addCredit(
                userId, request.getAmount(), request.getReason(), request.getExpirationDate()));
    }

    @PostMapping("/ledger/{userId}/deduct")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<LedgerDto> deductCredit(
            @PathVariable Long userId, @Valid @RequestBody AddCreditRequest request) {
        return ResponseEntity.ok(ledgerService.deductCredit(
                userId, request.getAmount(), request.getReason()));
    }

    @PatchMapping("/ledger/entry/{entryId}")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<LedgerDto> updateLedgerEntry(
            @PathVariable Long entryId, @RequestBody Map<String, String> body) {
        LocalDateTime expiration = body.get("expirationDate") != null
                ? LocalDateTime.parse(body.get("expirationDate")) : null;
        return ResponseEntity.ok(ledgerService.updateLedgerEntry(entryId, expiration));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<UserDto>> getAllUsers(Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        return ResponseEntity.ok(userService.getAllUsers(clubId));
    }

    @GetMapping("/users/search")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN', 'TRAINER')")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam String q, Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        return ResponseEntity.ok(userService.searchUsers(clubId, q));
    }

    @PatchMapping("/users/{id}/basic-training")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<UserDto> setBasicTraining(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean finished = Boolean.TRUE.equals(body.get("finished"));
        return ResponseEntity.ok(userService.setBasicTrainingFinished(id, finished));
    }

    @GetMapping("/analytics/occupancy")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<AnalyticsDto>> getOccupancyAnalytics(Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        return ResponseEntity.ok(analyticsService.getOccupancyLast7Days(clubId));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String filter) {
        if (filter != null && !filter.isBlank()) {
            // Cap the filter length so pathological inputs can't stall the LIKE query
            if (filter.length() > 100) {
                throw new com.rowingclub.backend.exception.BusinessException("filter too long (max 100 chars)");
            }
            return ResponseEntity.ok(auditLogRepository.findByActionContainingIgnoreCaseOrderByTimestampDesc(filter));
        }
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByTimestampDesc());
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<AdminMessage>> getMessages(Authentication auth) {
        Long clubId = sessionService.getClubIdForUser(auth.getName());
        if (clubId != null) {
            return ResponseEntity.ok(adminMessageRepository.findByClubIdAndIsResolvedFalseOrderByCreatedAtDesc(clubId));
        }
        return ResponseEntity.ok(adminMessageRepository.findByIsResolvedFalseOrderByCreatedAtDesc());
    }

    @PatchMapping("/messages/{id}/resolve")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, String>> resolveMessage(@PathVariable Long id) {
        AdminMessage msg = adminMessageRepository.findById(id).orElseThrow();
        msg.setIsResolved(true);
        adminMessageRepository.save(msg);
        return ResponseEntity.ok(Map.of("message", "Message resolved"));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, String>> getSettings() {
        List<AppSetting> settings = appSettingRepository.findAll();
        Map<String, String> map = new java.util.HashMap<>();
        settings.forEach(s -> map.put(s.getSettingKey(), s.getSettingValue()));
        return ResponseEntity.ok(map);
    }

    @PutMapping("/settings/{key}")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, String>> updateSetting(
            @PathVariable String key, @RequestBody Map<String, String> body) {
        AppSetting setting = appSettingRepository.findById(key)
                .orElse(AppSetting.builder().settingKey(key).build());
        setting.setSettingValue(body.get("value"));
        appSettingRepository.save(setting);
        return ResponseEntity.ok(Map.of("message", "Setting updated"));
    }

    @PostMapping("/scheduler/run")
    @PreAuthorize("hasAnyAuthority('CLUB_ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> runScheduler(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        var result = autoSchedulerService.runScheduler(weekStart);
        return ResponseEntity.ok(result);
    }
}
