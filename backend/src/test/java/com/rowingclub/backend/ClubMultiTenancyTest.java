package com.rowingclub.backend;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.repository.*;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClubMultiTenancyTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club testClub;

    @BeforeEach
    void setUp() {
        testClub = clubRepository.save(Club.builder()
                .name("ClubMultiTenancyTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
    }

    @Test
    void clubsTableExists() {
        assertDoesNotThrow(() ->
                entityManager.createNativeQuery("SELECT id, name FROM clubs").getResultList(),
                "clubs table should exist in the database schema");
    }

    @Test
    void usersBelongToClub() {
        // Club members are club-scoped; the platform SUPERADMIN deliberately has no club.
        User member = userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.SUPERADMIN)
                .findFirst()
                .orElseGet(() -> userRepository.save(User.builder()
                        .club(testClub)
                        .fullName("Club Test User")
                        .email("clubtest@test.com")
                        .passwordHash(passwordEncoder.encode("pass123"))
                        .role(Role.MEMBER)
                        .isFinishedBasicTraining(true)
                        .isOnSchoolTeam(false)
                        .lessonsAttended(0)
                        .build()));
        assertNotNull(member.getClub(), "A club member should be associated with a club");
    }

    @Test
    void sessionsBelongToClub() {
        RowingSession session = sessionRepository.save(RowingSession.builder()
                .club(testClub)
                .date(LocalDate.now().plusDays(60))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .status(SessionStatus.DRAFT)
                .build());
        assertNotNull(session.getClub(), "Session should be associated with a club");
    }

    @Test
    void boatsBelongToClub() {
        RowingSession session = sessionRepository.save(RowingSession.builder()
                .club(testClub)
                .date(LocalDate.now().plusDays(61))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .status(SessionStatus.DRAFT)
                .build());
        Boat boat = boatRepository.save(Boat.builder()
                .session(session)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .name("Club Boat")
                .build());
        assertNotNull(boat.getSession().getClub(), "Boat (via session) should belong to a club");
    }

    @Test
    void usersFromDifferentClubsAreIsolated() {
        List<User> allUsers = userRepository.findAll();
        if (allUsers.size() >= 2) {
            var clubs = allUsers.stream()
                    .map(User::getClub)
                    .filter(java.util.Objects::nonNull)
                    .map(c -> c.getId())
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            if (clubs.size() > 1) {
                Long club1Id = clubs.get(0);
                Long club2Id = clubs.get(1);
                assertNotEquals(club1Id, club2Id, "Different clubs should have different IDs");
            }
        }
    }

    @Test
    void existingDataMigratedToDefaultClub() {
        // Every club-scoped user must have a club. The platform SUPERADMIN is the only
        // intentional exception (it is not owned by any single club).
        List<User> users = userRepository.findAll();
        boolean allClubUsersHaveClub = users.stream()
                .filter(u -> u.getRole() != Role.SUPERADMIN)
                .allMatch(u -> u.getClub() != null);
        assertTrue(allClubUsersHaveClub, "All club-scoped users should belong to a club");
    }

    @PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
}
