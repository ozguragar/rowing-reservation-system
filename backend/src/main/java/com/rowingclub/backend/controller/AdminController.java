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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
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

    // Session Management
    @PostMapping("/sessions")
    public ResponseEntity<SessionDto> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.ok(sessionService.createSession(request));
    }

    @PostMapping("/sessions/bulk")
    public ResponseEntity<List<SessionDto>> createBulkSessions(@Valid @RequestBody BulkSessionRequest request) {
        return ResponseEntity.ok(sessionService.createBulkSessions(request.getSessions()));
    }

    @PostMapping("/sessions/{id}/boats")
    public ResponseEntity<BoatDto> addBoat(@PathVariable Long id, @Valid @RequestBody AddBoatRequest request) {
        return ResponseEntity.ok(sessionService.addBoatToSession(id, request));
    }

    @PatchMapping("/sessions/{id}/approve")
    public ResponseEntity<SessionDto> approveSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.approveSession(id));
    }

    @PostMapping("/sessions/bulk-approve")
    public ResponseEntity<List<SessionDto>> bulkApprove(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(sessionService.bulkApprove(ids));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.ok(Map.of("message", "Session deleted"));
    }

    @PostMapping("/sessions/bulk-delete")
    public ResponseEntity<Map<String, String>> bulkDeleteSessions(@RequestBody List<Long> ids) {
        sessionService.bulkDeleteSessions(ids);
        return ResponseEntity.ok(Map.of("message", "Sessions deleted"));
    }

    @DeleteMapping("/boats/{id}")
    public ResponseEntity<Map<String, String>> deleteBoat(@PathVariable Long id) {
        sessionService.deleteBoat(id);
        return ResponseEntity.ok(Map.of("message", "Boat deleted"));
    }

    @PostMapping("/sessions/copy-day")
    public ResponseEntity<List<SessionDto>> copyDay(@Valid @RequestBody CopyDayRequest request) {
        return ResponseEntity.ok(sessionService.copyDaySessions(request.getSourceDate(), request.getTargetDate()));
    }

    @PostMapping("/sessions/copy-week")
    public ResponseEntity<List<SessionDto>> copyWeek(@Valid @RequestBody CopyWeekRequest request) {
        return ResponseEntity.ok(sessionService.copyWeekSessions(
                request.getSourceWeekStart(), request.getTargetWeekStart()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionDto>> getAllSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(sessionService.getAllSessionsByDateRange(start, end));
    }

    // Booking Management
    @PostMapping("/bookings")
    public ResponseEntity<BookingDto> adminBook(@Valid @RequestBody AdminBookRequest request) {
        return ResponseEntity.ok(bookingService.adminBookUser(
                request.getUserId(), request.getBoatId(), request.getSessionId()));
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Map<String, String>> adminRemoveBooking(@PathVariable Long id) {
        bookingService.adminRemoveBooking(id);
        return ResponseEntity.ok(Map.of("message", "Booking removed and credit refunded"));
    }

    @PostMapping("/bookings/move")
    public ResponseEntity<BookingDto> adminMoveUser(@Valid @RequestBody AdminMoveRequest request) {
        return ResponseEntity.ok(bookingService.adminMoveUser(
                request.getUserId(), request.getFromBoatId(), request.getToBoatId()));
    }

    @GetMapping("/cancellation-requests")
    public ResponseEntity<List<BookingDto>> getPendingCancellations() {
        return ResponseEntity.ok(bookingService.getPendingCancellations());
    }

    @PostMapping("/cancellation-requests/{id}/approve")
    public ResponseEntity<BookingDto> approveCancellation(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.approveCancellation(id));
    }

    @PostMapping("/cancellation-requests/{id}/deny")
    public ResponseEntity<BookingDto> denyCancellation(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.denyCancellation(id));
    }

    // Ledger Management
    @GetMapping("/ledger/{userId}")
    public ResponseEntity<List<LedgerDto>> getUserLedger(@PathVariable Long userId) {
        return ResponseEntity.ok(ledgerService.getUserLedger(userId));
    }

    @PostMapping("/ledger/{userId}/credit")
    public ResponseEntity<LedgerDto> addCredit(
            @PathVariable Long userId, @Valid @RequestBody AddCreditRequest request) {
        return ResponseEntity.ok(ledgerService.addCredit(
                userId, request.getAmount(), request.getReason(), request.getExpirationDate()));
    }

    @PostMapping("/ledger/{userId}/deduct")
    public ResponseEntity<LedgerDto> deductCredit(
            @PathVariable Long userId, @Valid @RequestBody AddCreditRequest request) {
        return ResponseEntity.ok(ledgerService.deductCredit(
                userId, request.getAmount(), request.getReason()));
    }

    @PatchMapping("/ledger/entry/{entryId}")
    public ResponseEntity<LedgerDto> updateLedgerEntry(
            @PathVariable Long entryId, @RequestBody Map<String, String> body) {
        LocalDateTime expiration = body.get("expirationDate") != null
                ? LocalDateTime.parse(body.get("expirationDate")) : null;
        return ResponseEntity.ok(ledgerService.updateLedgerEntry(entryId, expiration));
    }

    // User Management
    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(userService.searchUsers(q));
    }

    @PatchMapping("/users/{id}/basic-training")
    public ResponseEntity<UserDto> setBasicTraining(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean finished = Boolean.TRUE.equals(body.get("finished"));
        return ResponseEntity.ok(userService.setBasicTrainingFinished(id, finished));
    }

    // Analytics
    @GetMapping("/analytics/occupancy")
    public ResponseEntity<List<AnalyticsDto>> getOccupancyAnalytics() {
        return ResponseEntity.ok(analyticsService.getOccupancyLast7Days());
    }

    // Audit Logs
    @GetMapping("/audit-logs")
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

    // Admin Messages
    @GetMapping("/messages")
    public ResponseEntity<List<AdminMessage>> getMessages() {
        return ResponseEntity.ok(adminMessageRepository.findByIsResolvedFalseOrderByCreatedAtDesc());
    }

    @PatchMapping("/messages/{id}/resolve")
    public ResponseEntity<Map<String, String>> resolveMessage(@PathVariable Long id) {
        AdminMessage msg = adminMessageRepository.findById(id).orElseThrow();
        msg.setIsResolved(true);
        adminMessageRepository.save(msg);
        return ResponseEntity.ok(Map.of("message", "Message resolved"));
    }

    // Settings
    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getSettings() {
        List<AppSetting> settings = appSettingRepository.findAll();
        Map<String, String> map = new java.util.HashMap<>();
        settings.forEach(s -> map.put(s.getSettingKey(), s.getSettingValue()));
        return ResponseEntity.ok(map);
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<Map<String, String>> updateSetting(
            @PathVariable String key, @RequestBody Map<String, String> body) {
        AppSetting setting = appSettingRepository.findById(key)
                .orElse(AppSetting.builder().settingKey(key).build());
        setting.setSettingValue(body.get("value"));
        appSettingRepository.save(setting);
        return ResponseEntity.ok(Map.of("message", "Setting updated"));
    }

    // Auto-scheduler
    @PostMapping("/scheduler/run")
    public ResponseEntity<Map<String, Object>> runScheduler(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        var result = autoSchedulerService.runScheduler(weekStart);
        return ResponseEntity.ok(result);
    }
}
