package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.Boat;
import com.rowingclub.backend.enums.BoatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface BoatRepository extends JpaRepository<Boat, Long> {
    List<Boat> findBySessionId(Long sessionId);

    /** Batch-load boats for many sessions at once (avoids per-session N+1). */
    List<Boat> findBySessionIdIn(Collection<Long> sessionIds);

    @Query("SELECT b FROM Boat b WHERE b.session.id = :sessionId AND b.currentBookings < b.capacity")
    List<Boat> findAvailableBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT b FROM Boat b WHERE b.session.id = :sessionId AND b.type = :type AND b.capacity = :capacity AND b.currentBookings < b.capacity")
    List<Boat> findAvailableBySessionIdAndTypeAndCapacity(
        @Param("sessionId") Long sessionId,
        @Param("type") BoatType type,
        @Param("capacity") Integer capacity
    );
}
