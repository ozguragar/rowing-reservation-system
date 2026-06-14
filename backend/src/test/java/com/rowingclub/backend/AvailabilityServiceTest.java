package com.rowingclub.backend;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvailabilityServiceTest {

    @Autowired private AvailabilityService availabilityService;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private UserAvailabilityRepository availabilityRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private User user;
    private RowingSession session;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("AvailabilityServiceTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        user = userRepository.save(User.builder()
                .fullName("Avail User").email("avail@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        session = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
    }

    @Test
    void setAvailabilityAddsRecord() {
        availabilityService.setAvailability(user.getEmail(), session.getId());
        assertTrue(availabilityRepository.existsByUserIdAndSessionId(user.getId(), session.getId()));
    }

    @Test
    void setAvailabilityIsIdempotent() {
        availabilityService.setAvailability(user.getEmail(), session.getId());
        availabilityService.setAvailability(user.getEmail(), session.getId()); // second call
        long count = availabilityRepository.findByUserId(user.getId()).stream()
                .filter(a -> a.getSession().getId().equals(session.getId())).count();
        assertEquals(1, count);
    }

    @Test
    void removeAvailabilityDeletesRecord() {
        availabilityService.setAvailability(user.getEmail(), session.getId());
        availabilityService.removeAvailability(user.getEmail(), session.getId());
        assertFalse(availabilityRepository.existsByUserIdAndSessionId(user.getId(), session.getId()));
    }

    @Test
    void getUserAvailableSessionsReturnsSessionIds() {
        availabilityService.setAvailability(user.getEmail(), session.getId());
        List<Long> ids = availabilityService.getUserAvailableSessions(user.getEmail());
        assertTrue(ids.contains(session.getId()));
    }

    @Test
    void getWeekSessionsReturnsOnlyWithinWeek() {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(monday).startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0)).status(SessionStatus.APPROVED).build());

        var weekSessions = availabilityService.getWeekSessions(monday);
        assertTrue(weekSessions.stream().anyMatch(s -> s.getDate().equals(monday)));
        assertTrue(weekSessions.stream().noneMatch(s -> s.getDate().isBefore(monday)));
        assertTrue(weekSessions.stream().noneMatch(s -> s.getDate().isAfter(monday.plusDays(6))));
    }
}
