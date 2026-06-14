package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.UserAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface UserAvailabilityRepository extends JpaRepository<UserAvailability, Long> {
    List<UserAvailability> findByUserId(Long userId);
    List<UserAvailability> findBySessionId(Long sessionId);
    boolean existsByUserIdAndSessionId(Long userId, Long sessionId);
    void deleteByUserIdAndSessionId(Long userId, Long sessionId);

    @Query("SELECT ua FROM UserAvailability ua WHERE ua.session.date BETWEEN :start AND :end")
    List<UserAvailability> findBySessionDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT ua FROM UserAvailability ua JOIN FETCH ua.user WHERE ua.session.id = :sessionId")
    List<UserAvailability> findBySessionIdWithUser(@Param("sessionId") Long sessionId);
}
