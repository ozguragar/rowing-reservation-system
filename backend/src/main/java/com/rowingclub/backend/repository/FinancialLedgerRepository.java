package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.FinancialLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FinancialLedgerRepository extends JpaRepository<FinancialLedger, Long> {
    List<FinancialLedger> findByUserIdOrderByTimestampDesc(Long userId);

    Optional<FinancialLedger> findFirstByUserIdOrderByTimestampDesc(Long userId);

    @Query("SELECT COALESCE(SUM(fl.amount), 0) FROM FinancialLedger fl WHERE fl.user.id = :userId")
    BigDecimal calculateBalance(@Param("userId") Long userId);

    @Query("SELECT fl FROM FinancialLedger fl WHERE fl.user.id = :userId AND fl.amount > 0 AND fl.expirationDate IS NOT NULL AND fl.expirationDate > :now ORDER BY fl.expirationDate ASC")
    List<FinancialLedger> findActiveCreditsWithExpiration(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT fl.user.id FROM FinancialLedger fl WHERE fl.amount > 0 AND fl.expirationDate IS NOT NULL AND fl.expirationDate BETWEEN :start AND :end")
    List<Long> findUserIdsWithExpiringCredits(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT fl.user.id, COALESCE(SUM(fl.amount), 0) FROM FinancialLedger fl GROUP BY fl.user.id HAVING COALESCE(SUM(fl.amount), 0) > 0")
    List<Object[]> findUsersWithPositiveBalance();
}
