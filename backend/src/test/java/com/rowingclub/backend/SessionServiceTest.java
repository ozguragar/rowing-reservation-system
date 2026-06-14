package com.rowingclub.backend;

import com.rowingclub.backend.dto.AddBoatRequest;
import com.rowingclub.backend.dto.CreateSessionRequest;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
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
class SessionServiceTest {

    @Autowired private SessionService sessionService;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private ClubRepository clubRepository;

    private Club testClub;

    // Use dates >14 days out to avoid DataSeeder-seeded sessions
    private static final int OFFSET = 50;

    @BeforeEach
    void setUp() {
        testClub = clubRepository.save(Club.builder()
                .name("SessionServiceTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
    }

    private RowingSession makeSession(LocalDate date) {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setDate(date);
        req.setStartTime(LocalTime.of(8, 0));
        req.setEndTime(LocalTime.of(9, 0));
        var dto = sessionService.createSession(req, testClub);
        return sessionRepository.findById(dto.getId()).orElseThrow();
    }

    @Test
    void createSessionDefaultsDraft() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setDate(LocalDate.now().plusDays(OFFSET + 1));
        req.setStartTime(LocalTime.of(6, 20));
        req.setEndTime(LocalTime.of(7, 20));
        var dto = sessionService.createSession(req, testClub);
        assertEquals("DRAFT", dto.getStatus());
    }

    @Test
    void addCoastalBoatValidCapacity() {
        RowingSession session = makeSession(LocalDate.now().plusDays(OFFSET + 2));
        AddBoatRequest req = new AddBoatRequest();
        req.setType("COASTAL");
        req.setCapacity(4);
        req.setIsBasicTrainingBoat(false);
        req.setName("4x Coastal");
        var boat = sessionService.addBoatToSession(session.getId(), req);
        assertEquals(4, boat.getCapacity());
        assertEquals("COASTAL", boat.getType());
    }

    @Test
    void addCoastalBoatInvalidCapacityThrows() {
        RowingSession session = makeSession(LocalDate.now().plusDays(OFFSET + 3));
        AddBoatRequest req = new AddBoatRequest();
        req.setType("COASTAL");
        req.setCapacity(3); // invalid
        req.setIsBasicTrainingBoat(false);
        assertThrows(BusinessException.class, () -> sessionService.addBoatToSession(session.getId(), req));
    }

    @Test
    void addOlympicBoatCapacity8Valid() {
        RowingSession session = makeSession(LocalDate.now().plusDays(OFFSET + 4));
        AddBoatRequest req = new AddBoatRequest();
        req.setType("OLYMPIC");
        req.setCapacity(8);
        req.setIsBasicTrainingBoat(false);
        var boat = sessionService.addBoatToSession(session.getId(), req);
        assertEquals(8, boat.getCapacity());
    }

    @Test
    void approveSessionFlipsDraftToApproved() {
        RowingSession session = makeSession(LocalDate.now().plusDays(OFFSET + 5));
        assertEquals(SessionStatus.DRAFT, session.getStatus());
        sessionService.approveSession(session.getId());
        RowingSession refreshed = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(SessionStatus.APPROVED, refreshed.getStatus());
    }

    @Test
    void copyDayClonesSessionAndBoats() {
        LocalDate source = LocalDate.now().plusDays(OFFSET + 6);
        RowingSession orig = makeSession(source);
        boatRepository.save(Boat.builder()
                .session(orig).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x A").build());

        LocalDate target = LocalDate.now().plusDays(OFFSET + 7);
        var copies = sessionService.copyDaySessions(source, target, null);

        assertEquals(1, copies.size());
        assertEquals(target, copies.get(0).getDate());
        assertFalse(copies.get(0).getBoats().isEmpty());
        assertEquals(0, copies.get(0).getBoats().get(0).getCurrentBookings());
    }

    @Test
    void copyWeekClonesAllDays() {
        LocalDate weekStart = LocalDate.now().plusDays(OFFSET + 10);
        makeSession(weekStart.plusDays(1));
        makeSession(weekStart.plusDays(2));
        makeSession(weekStart.plusDays(3));

        LocalDate targetWeek = LocalDate.now().plusDays(OFFSET + 20);
        var copies = sessionService.copyWeekSessions(weekStart, targetWeek, null);

        assertEquals(3, copies.size());
        assertTrue(copies.stream().allMatch(s -> "DRAFT".equals(s.getStatus())));
    }

    @Test
    void getApprovedUpcomingSessionsFiltersCorrectly() {
        LocalDate future = LocalDate.now().plusDays(OFFSET + 30);
        RowingSession toApprove = makeSession(future);
        sessionService.approveSession(toApprove.getId());

        RowingSession draftOnly = makeSession(LocalDate.now().plusDays(OFFSET + 31));
        // draftOnly stays DRAFT

        var upcoming = sessionService.getApprovedUpcomingSessions();
        assertTrue(upcoming.stream().anyMatch(s -> s.getId().equals(toApprove.getId())));
        assertTrue(upcoming.stream().noneMatch(s -> s.getId().equals(draftOnly.getId())));
    }

    @Test
    void deleteSessionWithNoBookingsSucceeds() {
        RowingSession session = makeSession(LocalDate.now().plusDays(OFFSET + 40));
        Long id = session.getId();
        sessionService.deleteSession(id);
        assertFalse(sessionRepository.existsById(id));
    }

    @Test
    void bulkDeleteRemovesAllGiven() {
        RowingSession s1 = makeSession(LocalDate.now().plusDays(OFFSET + 41));
        RowingSession s2 = makeSession(LocalDate.now().plusDays(OFFSET + 42));
        sessionService.bulkDeleteSessions(List.of(s1.getId(), s2.getId()));
        assertFalse(sessionRepository.existsById(s1.getId()));
        assertFalse(sessionRepository.existsById(s2.getId()));
    }
}
