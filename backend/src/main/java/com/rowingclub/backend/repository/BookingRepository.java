package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.Booking;
import com.rowingclub.backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByBoatId(Long boatId);
    List<Booking> findBySessionId(Long sessionId);
    List<Booking> findByBoatIdAndStatusNot(Long boatId, BookingStatus status);

    /**
     * Batch-load non-canceled bookings for many boats at once, eagerly fetching the
     * user and boat so {@code BookingDto.from} does not trigger a query per booking.
     */
    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.boat "
            + "WHERE b.boat.id IN :boatIds AND b.status <> :status")
    List<Booking> findByBoatIdInAndStatusNotFetchUser(@Param("boatIds") Collection<Long> boatIds,
                                                      @Param("status") BookingStatus status);

    Optional<Booking> findByUserIdAndSessionIdAndStatusNot(Long userId, Long sessionId, BookingStatus status);
    boolean existsByUserIdAndSessionIdAndStatusNot(Long userId, Long sessionId, BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId AND b.status != 'CANCELED' AND b.session.date >= :fromDate ORDER BY b.session.date, b.session.startTime")
    List<Booking> findActiveBookingsByUserId(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate);

    /** All of a user's non-canceled bookings (past + future), newest first, with session/boat fetched. */
    @Query("SELECT b FROM Booking b JOIN FETCH b.session JOIN FETCH b.boat "
            + "WHERE b.user.id = :userId AND b.status <> 'CANCELED' "
            + "ORDER BY b.session.date DESC, b.session.startTime DESC")
    List<Booking> findActiveByUserIdFetchSessionBoat(@Param("userId") Long userId);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.session.date = :date AND b.status != 'CANCELED'")
    long countActiveBookingsByDate(@Param("date") LocalDate date);

    @Query("SELECT b FROM Booking b WHERE b.session.date BETWEEN :start AND :end AND b.status != 'CANCELED'")
    List<Booking> findActiveBookingsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Booking> findByStatusOrderByCreatedAtAsc(BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.session.club.id = :clubId ORDER BY b.createdAt ASC")
    List<Booking> findByStatusAndClubIdOrderByCreatedAtAsc(@Param("status") BookingStatus status, @Param("clubId") Long clubId);
}
