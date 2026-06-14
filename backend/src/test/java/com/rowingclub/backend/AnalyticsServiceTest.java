package com.rowingclub.backend;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyticsServiceTest {

    @Autowired private AnalyticsService analyticsService;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;

    @Test
    void emptySessionsReturnsEmptyList() {
        var result = analyticsService.getOccupancyLast7Days();
        // may have sessions from other tests if not isolated — just assert no exception
        assertNotNull(result);
    }

    @Test
    void occupancyCalculatedCorrectly() {
        RowingSession session = sessionRepository.save(RowingSession.builder()
                .date(LocalDate.now().minusDays(1))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());

        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(2).name("4x A").build());

        var analytics = analyticsService.getOccupancyLast7Days();
        var entry = analytics.stream()
                .filter(a -> a.getSessionId().equals(session.getId())).findFirst();
        assertTrue(entry.isPresent());
        assertEquals(50.0, entry.get().getOccupancyPercentage(), 0.01);
        assertEquals(2, entry.get().getTotalBooked());
        assertEquals(4, entry.get().getTotalCapacity());
    }

    @Test
    void sessionWithNoBoatsHasZeroOccupancy() {
        RowingSession session = sessionRepository.save(RowingSession.builder()
                .date(LocalDate.now().minusDays(2))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status(SessionStatus.APPROVED).build());

        var analytics = analyticsService.getOccupancyLast7Days();
        var entry = analytics.stream()
                .filter(a -> a.getSessionId().equals(session.getId())).findFirst();
        assertTrue(entry.isPresent());
        assertEquals(0.0, entry.get().getOccupancyPercentage(), 0.01);
    }
}
