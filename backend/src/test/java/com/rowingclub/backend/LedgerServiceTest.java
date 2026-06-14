package com.rowingclub.backend;

import com.rowingclub.backend.entity.FinancialLedger;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.FinancialLedgerRepository;
import com.rowingclub.backend.repository.UserRepository;
import com.rowingclub.backend.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerServiceTest {

    @Autowired private LedgerService ledgerService;
    @Autowired private UserRepository userRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .fullName("Ledger Test User")
                .email("ledger@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.STUDENT)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());
    }

    @Test
    void addCreditIncreasesBalance() {
        ledgerService.addCredit(testUser.getId(), BigDecimal.TEN, "Test credit", null);
        assertEquals(0, BigDecimal.TEN.compareTo(ledgerService.getBalance(testUser.getId())));
    }

    @Test
    void deductCreditDecreasesBalance() {
        ledgerService.addCredit(testUser.getId(), BigDecimal.TEN, "Initial", null);
        ledgerService.deductCredit(testUser.getId(), BigDecimal.valueOf(3), "Deduction");
        assertEquals(0, BigDecimal.valueOf(7).compareTo(ledgerService.getBalance(testUser.getId())));
    }

    @Test
    void cannotDeductMoreThanBalance() {
        ledgerService.addCredit(testUser.getId(), BigDecimal.valueOf(2), "Small credit", null);
        assertThrows(BusinessException.class, () ->
                ledgerService.deductCredit(testUser.getId(), BigDecimal.TEN, "Too much"));
    }

    @Test
    void runningBalanceIsAccurate() {
        var entry1 = ledgerService.addCredit(testUser.getId(), BigDecimal.TEN, "First", null);
        assertEquals(0, BigDecimal.TEN.compareTo(entry1.getRunningBalance()));

        var entry2 = ledgerService.deductCredit(testUser.getId(), BigDecimal.valueOf(4), "Second");
        assertEquals(0, BigDecimal.valueOf(6).compareTo(entry2.getRunningBalance()));

        var entry3 = ledgerService.addCredit(testUser.getId(), BigDecimal.valueOf(2), "Third", null);
        assertEquals(0, BigDecimal.valueOf(8).compareTo(entry3.getRunningBalance()));
    }

    @Test
    void expirationDateIsStored() {
        LocalDateTime expDate = LocalDateTime.now().plusMonths(1);
        var entry = ledgerService.addCredit(testUser.getId(), BigDecimal.TEN, "With expiry", expDate);
        assertNotNull(entry.getExpirationDate());
    }

    @Test
    void refundCreditProducesPositiveRow() {
        ledgerService.addCredit(testUser.getId(), BigDecimal.TEN, "Initial", null);
        ledgerService.deductCredit(testUser.getId(), BigDecimal.valueOf(3), "Use");
        var refund = ledgerService.refundCredit(testUser.getId(), BigDecimal.ONE, "Refund");
        assertTrue(refund.getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void updateLedgerEntryChangesExpiration() {
        var entry = ledgerService.addCredit(testUser.getId(), BigDecimal.TEN, "Initial", null);
        LocalDateTime newExp = LocalDateTime.now().plusMonths(2);
        var updated = ledgerService.updateLedgerEntry(entry.getId(), newExp);
        assertNotNull(updated.getExpirationDate());
    }

    @Test
    void multipleEntriesBalanceCalculation() {
        ledgerService.addCredit(testUser.getId(), BigDecimal.valueOf(5), "A", null);
        ledgerService.addCredit(testUser.getId(), BigDecimal.valueOf(3), "B", null);
        ledgerService.deductCredit(testUser.getId(), BigDecimal.valueOf(2), "C");
        BigDecimal balance = ledgerService.getBalance(testUser.getId());
        assertEquals(0, BigDecimal.valueOf(6).compareTo(balance));
    }

    @Test
    void activeCreditsWithExpirationReturnsOnlyFuture() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        LocalDateTime future = LocalDateTime.now().plusDays(10);
        ledgerService.addCredit(testUser.getId(), BigDecimal.valueOf(5), "Past", past);
        ledgerService.addCredit(testUser.getId(), BigDecimal.valueOf(5), "Future", future);
        var active = ledgerService.getActiveCreditsWithExpiration(testUser.getId());
        assertEquals(1, active.size());
        assertTrue(active.get(0).getExpirationDate().isAfter(LocalDateTime.now()));
    }
}
