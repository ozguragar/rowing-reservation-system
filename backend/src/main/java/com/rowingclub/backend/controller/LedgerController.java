package com.rowingclub.backend.controller;

import com.rowingclub.backend.dto.AdminMessageRequest;
import com.rowingclub.backend.dto.LedgerDto;
import com.rowingclub.backend.entity.AdminMessage;
import com.rowingclub.backend.entity.FinancialLedger;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.AdminMessageRepository;
import com.rowingclub.backend.repository.FinancialLedgerRepository;
import com.rowingclub.backend.repository.UserRepository;
import com.rowingclub.backend.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final AdminMessageRepository adminMessageRepository;
    private final FinancialLedgerRepository ledgerRepository;

    @GetMapping("/my")
    public ResponseEntity<List<LedgerDto>> getMyLedger(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(ledgerService.getUserLedger(user.getId()));
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getMyBalance(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BigDecimal balance = ledgerService.getBalance(user.getId());
        LocalDateTime expiration = ledgerService.getEarliestExpiration(user.getId());
        return ResponseEntity.ok(Map.of(
                "balance", balance,
                "expirationDate", expiration != null ? expiration.toString() : "none"
        ));
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, String>> reportLedgerEntry(
            Authentication auth, @Valid @RequestBody AdminMessageRequest request) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Access gate: only allow reporting own ledger entries
        FinancialLedger entry = ledgerRepository.findById(request.getLedgerEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Ledger entry not found"));
        if (!entry.getUser().getId().equals(user.getId())) {
            throw new BusinessException("You can only report your own ledger entries");
        }

        AdminMessage message = AdminMessage.builder()
                .user(user)
                .ledgerEntryId(request.getLedgerEntryId())
                .message(request.getMessage())
                .build();
        adminMessageRepository.save(message);

        return ResponseEntity.ok(Map.of("message", "Report sent to admin"));
    }
}
