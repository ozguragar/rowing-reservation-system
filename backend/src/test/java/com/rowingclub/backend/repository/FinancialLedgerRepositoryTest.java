package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class FinancialLedgerRepositoryTest {

    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .fullName("Ledger Repo User").email("ledgerrepo@test.com")
                .passwordHash("hash").role(Role.STUDENT)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
    }

    private void saveEntry(BigDecimal amount, LocalDateTime expiration) {
        BigDecimal balance = ledgerRepository.calculateBalance(user.getId()).add(amount);
        ledgerRepository.save(FinancialLedger.builder()
                .user(user).amount(amount).reason("Test")
                .runningBalance(balance).timestamp(LocalDateTime.now())
                .expirationDate(expiration).build());
    }

    @Test
    void calculateBalance_sumsAllAmounts() {
        saveEntry(BigDecimal.valueOf(10), null);
        saveEntry(BigDecimal.valueOf(-3), null);
        saveEntry(BigDecimal.valueOf(5), null);
        BigDecimal balance = ledgerRepository.calculateBalance(user.getId());
        assertEquals(0, BigDecimal.valueOf(12).compareTo(balance));
    }

    @Test
    void calculateBalance_zeroForNoEntries() {
        User fresh = userRepository.save(User.builder()
                .fullName("Fresh").email("fresh@test.com")
                .passwordHash("hash").role(Role.STUDENT)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
        assertEquals(0, BigDecimal.ZERO.compareTo(ledgerRepository.calculateBalance(fresh.getId())));
    }

    @Test
    void findActiveCreditsWithExpiration_returnsOnlyFuture() {
        saveEntry(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1)); // past
        saveEntry(BigDecimal.valueOf(5), LocalDateTime.now().plusDays(10)); // future
        saveEntry(BigDecimal.valueOf(5), null); // no expiry

        var active = ledgerRepository.findActiveCreditsWithExpiration(user.getId(), LocalDateTime.now());
        assertEquals(1, active.size());
    }

    @Test
    void findUsersWithPositiveBalance() {
        saveEntry(BigDecimal.valueOf(5), null);
        var result = ledgerRepository.findUsersWithPositiveBalance();
        assertTrue(result.stream().anyMatch(row -> {
            Long uid = ((Number) row[0]).longValue();
            BigDecimal bal = (BigDecimal) row[1];
            return uid.equals(user.getId()) && bal.compareTo(BigDecimal.ZERO) > 0;
        }));
    }
}
