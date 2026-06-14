package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.Booking;
import com.rowingclub.backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByBoatId(Long boatId);
    List<Booking> findBySessionId(Long sessionId);
    List<Booking> findByBoatIdAndStatusNot(Long boatId, BookingStatus status);

    Optional<Booking> findByUserIdAndSessionIdAndStatusNot(Long userId, Long sessionId, BookingStatus status);
    boolean existsByUserIdAndSessionIdAndStatusNot(Long userId, Long sessionId, BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId AND b.status != 'CANCELED' AND b.session.date >= :fromDate ORDER BY b.session.date, b.session.startTime")
    List<Booking> findActiveBookingsByUserId(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.session.date = :date AND b.status != 'CANCELED'")
    long countActiveBookingsByDate(@Param("date") LocalDate date);

    @Query("SELECT b FROM Booking b WHERE b.session.date BETWEEN :start AND :end AND b.status != 'CANCELED'")
    List<Booking> findActiveBookingsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Booking> findByStatusOrderByCreatedAtAsc(BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.session.club.id = :clubId ORDER BY b.createdAt ASC")
    List<Booking> findByStatusAndClubIdOrderByCreatedAtAsc(@Param("status") BookingStatus status, @Param("clubId") Long clubId);
}
