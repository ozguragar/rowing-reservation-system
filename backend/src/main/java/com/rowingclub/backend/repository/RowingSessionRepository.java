package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.RowingSession;
import com.rowingclub.backend.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface RowingSessionRepository extends JpaRepository<RowingSession, Long> {
    List<RowingSession> findByDateAndStatus(LocalDate date, SessionStatus status);
    List<RowingSession> findByDateBetweenAndStatus(LocalDate start, LocalDate end, SessionStatus status);
    List<RowingSession> findByDateBetween(LocalDate start, LocalDate end);
    List<RowingSession> findByDate(LocalDate date);
    List<RowingSession> findByDateGreaterThanEqualAndStatus(LocalDate date, SessionStatus status);
    List<RowingSession> findByStatus(SessionStatus status);

    List<RowingSession> findByClubIdAndDateBetween(Long clubId, LocalDate start, LocalDate end);
    List<RowingSession> findByClubIdAndDateGreaterThanEqualAndStatus(Long clubId, LocalDate date, SessionStatus status);
    List<RowingSession> findByClubIdAndDate(Long clubId, LocalDate date);
}
