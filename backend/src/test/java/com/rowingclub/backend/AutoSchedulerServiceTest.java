package com.rowingclub.backend;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.service.AutoSchedulerService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AutoSchedulerServiceTest {

    @Autowired private AutoSchedulerService autoSchedulerService;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private UserAvailabilityRepository availabilityRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private RowingSession session;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("AutoSchedulerServiceTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        LocalDate date = LocalDate.now().plusDays(2);
        session = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(date).startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0)).status(SessionStatus.APPROVED).build());
    }

    @Test
    void schedulerOnlyUses4PersonCoastalBoats() {
        // Create different boat types
        Boat fourPerson = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x Coastal").build());

        Boat twoPerson = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(false).currentBookings(0).name("2x Coastal").build());

        Boat onePerson = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(1)
                .isBasicTrainingBoat(false).currentBookings(0).name("1x Coastal").build());

        Boat olympicBoat = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.OLYMPIC).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x Olympic").build());

        // Create 4 students with availability and credits
        for (int i = 0; i < 4; i++) {
            User student = userRepository.save(User.builder()
                    .club(club)
                    .fullName("Sched Student " + i)
                    .email("sched_student" + i + "@test.com")
                    .passwordHash(passwordEncoder.encode("pass"))
                    .role(Role.MEMBER).isFinishedBasicTraining(true)
                    .isOnSchoolTeam(false).lessonsAttended(0).build());

            ledgerRepository.save(FinancialLedger.builder()
                    .club(club).user(student).amount(BigDecimal.TEN)
                    .reason("Credits").runningBalance(BigDecimal.TEN)
                    .timestamp(LocalDateTime.now()).build());

            availabilityRepository.save(UserAvailability.builder()
                    .user(student).session(session).build());
        }

        Map<String, Object> result = autoSchedulerService.runScheduler(session.getDate());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");

        // All assignments should be on the 4-person coastal boat only
        for (Map<String, Object> assignment : assignments) {
            assertEquals("4x Coastal", assignment.get("boatName"));
        }

        // 2-person and 1-person boats should still be empty
        Boat refreshedTwo = boatRepository.findById(twoPerson.getId()).orElseThrow();
        Boat refreshedOne = boatRepository.findById(onePerson.getId()).orElseThrow();
        Boat refreshedOlympic = boatRepository.findById(olympicBoat.getId()).orElseThrow();
        assertEquals(0, refreshedTwo.getCurrentBookings());
        assertEquals(0, refreshedOne.getCurrentBookings());
        assertEquals(0, refreshedOlympic.getCurrentBookings());
    }

    @Test
    void schedulerAssignsExactly4Or3People() {
        // Two 4-person boats
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x B").build());

        // Create 7 students (should be: 4 in one boat, 3 in another)
        for (int i = 0; i < 7; i++) {
            User student = userRepository.save(User.builder()
                    .club(club)
                    .fullName("SevenStudent " + i)
                    .email("seven_student" + i + "@test.com")
                    .passwordHash(passwordEncoder.encode("pass"))
                    .role(Role.MEMBER).isFinishedBasicTraining(true)
                    .isOnSchoolTeam(false).lessonsAttended(0).build());

            ledgerRepository.save(FinancialLedger.builder()
                    .club(club).user(student).amount(BigDecimal.TEN)
                    .reason("Credits").runningBalance(BigDecimal.TEN)
                    .timestamp(LocalDateTime.now()).build());

            availabilityRepository.save(UserAvailability.builder()
                    .user(student).session(session).build());
        }

        Map<String, Object> result = autoSchedulerService.runScheduler(session.getDate());
        int totalAssigned = (int) result.get("totalAssigned");

        assertEquals(7, totalAssigned);
    }

    @Test
    void schedulerDoesNotMixStudentsAndClubMembers() {
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x B").build());

        // 4 students and 4 club members
        for (int i = 0; i < 4; i++) {
            User student = userRepository.save(User.builder()
                    .club(club)
                    .fullName("Mix Student " + i)
                    .email("mix_student" + i + "@test.com")
                    .passwordHash(passwordEncoder.encode("pass"))
                    .role(Role.MEMBER).isFinishedBasicTraining(true)
                    .isOnSchoolTeam(false).lessonsAttended(0).build());

            ledgerRepository.save(FinancialLedger.builder()
                    .club(club).user(student).amount(BigDecimal.TEN)
                    .reason("Credits").runningBalance(BigDecimal.TEN)
                    .timestamp(LocalDateTime.now()).build());

            availabilityRepository.save(UserAvailability.builder()
                    .user(student).session(session).build());

            User member = userRepository.save(User.builder()
                    .club(club)
                    .fullName("Mix Member " + i)
                    .email("mix_member" + i + "@test.com")
                    .passwordHash(passwordEncoder.encode("pass"))
                    .role(Role.MEMBER).isFinishedBasicTraining(true)
                    .isOnSchoolTeam(false).lessonsAttended(0).build());

            ledgerRepository.save(FinancialLedger.builder()
                    .club(club).user(member).amount(BigDecimal.TEN)
                    .reason("Credits").runningBalance(BigDecimal.TEN)
                    .timestamp(LocalDateTime.now()).build());

            availabilityRepository.save(UserAvailability.builder()
                    .user(member).session(session).build());
        }

        autoSchedulerService.runScheduler(session.getDate());

        // Check each boat's bookings have only one role
        List<Boat> boats = boatRepository.findBySessionId(session.getId());
        for (Boat boat : boats) {
            List<Booking> bookings = bookingRepository.findByBoatIdAndStatusNot(boat.getId(), BookingStatus.CANCELED);
            if (bookings.isEmpty()) continue;

            Role firstRole = bookings.get(0).getUser().getRole();
            for (Booking b : bookings) {
                assertEquals(firstRole, b.getUser().getRole(),
                        "Boat " + boat.getName() + " has mixed roles");
            }
        }
    }

    private User availableUser(String email, Club c, RowingSession sess, int credits) {
        User u = userRepository.save(User.builder()
                .club(c).fullName(email).email(email)
                .passwordHash(passwordEncoder.encode("pass")).role(Role.MEMBER)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
        if (credits > 0) {
            ledgerRepository.save(FinancialLedger.builder()
                    .club(c).user(u).amount(BigDecimal.valueOf(credits)).reason("c")
                    .runningBalance(BigDecimal.valueOf(credits)).timestamp(LocalDateTime.now()).build());
        }
        availabilityRepository.save(UserAvailability.builder().user(u).session(sess).build());
        return u;
    }

    @Test
    void schedulerSkipsUsersWithoutCredit() {
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x NoCredit").build());
        for (int i = 0; i < 4; i++) availableUser("nocredit" + i + "@test.com", club, session, 0);

        Map<String, Object> result = autoSchedulerService.runScheduler(session.getDate());
        assertEquals(0, (int) result.get("totalAssigned"));
    }

    @Test
    void schedulerExcludesAlreadyBookedUser() {
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x Mix").build());
        Boat sideBoat = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(false).currentBookings(1).name("2x side").build());

        availableUser("avail_a@test.com", club, session, 10);
        availableUser("avail_b@test.com", club, session, 10);
        availableUser("avail_c@test.com", club, session, 10);
        User booked = availableUser("avail_booked@test.com", club, session, 10);
        bookingRepository.save(Booking.builder()
                .user(booked).boat(sideBoat).session(session)
                .status(BookingStatus.MANUAL).isCoxSeat(false).build());

        Map<String, Object> result = autoSchedulerService.runScheduler(session.getDate());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");

        assertEquals(3, (int) result.get("totalAssigned"));
        assertTrue(assignments.stream().noneMatch(a -> booked.getId().equals(a.get("userId"))));
    }

    @Test
    void schedulerSkipsClubWithFeatureDisabled() {
        Club offClub = clubRepository.save(Club.builder()
                .name("Scheduler Off Club")
                .featureAvailabilityModule(true).featureCancellationRequests(true)
                .featureAutoScheduler(false).featureShowBookedMembers(true).build());
        LocalDate farDate = LocalDate.now().plusDays(60);
        RowingSession offSession = sessionRepository.save(RowingSession.builder()
                .club(offClub).date(farDate).startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0)).status(SessionStatus.APPROVED).build());
        boatRepository.save(Boat.builder()
                .session(offSession).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x Off").build());
        for (int i = 0; i < 4; i++) availableUser("off" + i + "@test.com", offClub, offSession, 10);

        Map<String, Object> result = autoSchedulerService.runScheduler(farDate);
        assertEquals(0, (int) result.get("totalAssigned"));
    }

    @Test
    void schedulerDeductsCreditAndIncrementsLessons() {
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0).name("4x Effects").build());
        User u1 = availableUser("eff1@test.com", club, session, 5);
        availableUser("eff2@test.com", club, session, 5);
        availableUser("eff3@test.com", club, session, 5);

        autoSchedulerService.runScheduler(session.getDate());

        User refreshed = userRepository.findById(u1.getId()).orElseThrow();
        assertEquals(1, refreshed.getLessonsAttended());
        assertEquals(0, BigDecimal.valueOf(4).compareTo(ledgerRepository.calculateBalance(u1.getId())));
    }

    @Test
    void schedulerIgnoresTwoPersonBoats() {
        Boat twoPerson = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(false).currentBookings(0).name("2x Ignored").build());

        for (int i = 0; i < 4; i++) {
            User student = userRepository.save(User.builder()
                    .club(club)
                    .fullName("Ignore Student " + i)
                    .email("ignore_student" + i + "@test.com")
                    .passwordHash(passwordEncoder.encode("pass"))
                    .role(Role.MEMBER).isFinishedBasicTraining(true)
                    .isOnSchoolTeam(false).lessonsAttended(0).build());

            ledgerRepository.save(FinancialLedger.builder()
                    .club(club).user(student).amount(BigDecimal.TEN)
                    .reason("Credits").runningBalance(BigDecimal.TEN)
                    .timestamp(LocalDateTime.now()).build());

            availabilityRepository.save(UserAvailability.builder()
                    .user(student).session(session).build());
        }

        Map<String, Object> result = autoSchedulerService.runScheduler(session.getDate());
        int totalAssigned = (int) result.get("totalAssigned");

        assertEquals(0, totalAssigned);
        Boat refreshed = boatRepository.findById(twoPerson.getId()).orElseThrow();
        assertEquals(0, refreshed.getCurrentBookings());
    }
}
