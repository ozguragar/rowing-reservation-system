package com.rowingclub.backend.service;

import com.rowingclub.backend.dto.LedgerDto;
import com.rowingclub.backend.entity.FinancialLedger;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.FinancialLedgerRepository;
import com.rowingclub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final FinancialLedgerRepository ledgerRepository;
    private final UserRepository userRepository;

    public BigDecimal getBalance(Long userId) {
        return ledgerRepository.calculateBalance(userId);
    }

    public List<LedgerDto> getUserLedger(Long userId) {
        return ledgerRepository.findByUserIdOrderByTimestampDesc(userId).stream()
                .map(LedgerDto::from)
                .toList();
    }

    @Transactional
    public LedgerDto addCredit(Long userId, BigDecimal amount, String reason, LocalDateTime expirationDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BigDecimal currentBalance = ledgerRepository.calculateBalance(userId);
        BigDecimal newBalance = currentBalance.add(amount);

        FinancialLedger entry = FinancialLedger.builder()
                .user(user)
                .club(user.getClub())
                .amount(amount)
                .reason(reason != null ? reason : "Credit added by admin")
                .runningBalance(newBalance)
                .timestamp(LocalDateTime.now())
                .expirationDate(expirationDate)
                .build();

        return LedgerDto.from(ledgerRepository.save(entry));
    }

    @Transactional
    public LedgerDto deductCredit(Long userId, BigDecimal amount, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BigDecimal currentBalance = ledgerRepository.calculateBalance(userId);
        if (currentBalance.compareTo(amount) < 0) {
            throw new BusinessException("Insufficient credit balance");
        }

        BigDecimal newBalance = currentBalance.subtract(amount);

        FinancialLedger entry = FinancialLedger.builder()
                .user(user)
                .club(user.getClub())
                .amount(amount.negate())
                .reason(reason)
                .runningBalance(newBalance)
                .timestamp(LocalDateTime.now())
                .build();

        return LedgerDto.from(ledgerRepository.save(entry));
    }

    @Transactional
    public LedgerDto refundCredit(Long userId, BigDecimal amount, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BigDecimal currentBalance = ledgerRepository.calculateBalance(userId);
        BigDecimal newBalance = currentBalance.add(amount);

        FinancialLedger entry = FinancialLedger.builder()
                .user(user)
                .club(user.getClub())
                .amount(amount)
                .reason(reason)
                .runningBalance(newBalance)
                .timestamp(LocalDateTime.now())
                .build();

        return LedgerDto.from(ledgerRepository.save(entry));
    }

    public List<FinancialLedger> getActiveCreditsWithExpiration(Long userId) {
        return ledgerRepository.findActiveCreditsWithExpiration(userId, LocalDateTime.now());
    }

    public LocalDateTime getEarliestExpiration(Long userId) {
        List<FinancialLedger> activeCredits = getActiveCreditsWithExpiration(userId);
        return activeCredits.isEmpty() ? null : activeCredits.get(0).getExpirationDate();
    }

    @Transactional
    public LedgerDto updateLedgerEntry(Long entryId, LocalDateTime newExpirationDate) {
        FinancialLedger entry = ledgerRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger entry not found"));
        entry.setExpirationDate(newExpirationDate);
        return LedgerDto.from(ledgerRepository.save(entry));
    }
}
