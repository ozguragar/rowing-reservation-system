package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class BookingRepositoryTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private ClubRepository clubRepository;

    private User user;
    private Club club;
    private int dateOffset = 0;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("BookingRepoTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        user = userRepository.save(User.builder()
                .fullName("Repo User").email("repo@test.com")
                .passwordHash("hash").role(Role.MEMBER)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
    }

    private Boat makeBoat() {
        RowingSession session = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().plusDays(100 + dateOffset++))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
        return boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0).name("Boat").build());
    }

    private Booking save(BookingStatus status) {
        Boat boat = makeBoat();
        return bookingRepository.save(Booking.builder()
                .user(user).boat(boat).session(boat.getSession()).status(status).build());
    }

    @Test
    void findActiveBookingsByUserId_excludesCanceled() {
        save(BookingStatus.MANUAL);
        save(BookingStatus.CANCELED);
        var active = bookingRepository.findActiveBookingsByUserId(user.getId(), LocalDate.now());
        assertEquals(1, active.size());
        assertEquals(BookingStatus.MANUAL, active.get(0).getStatus());
    }

    @Test
    void findActiveBookingsByUserId_excludesPast() {
        RowingSession past = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().minusDays(1))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
        Boat pastBoat = boatRepository.save(Boat.builder()
                .session(past).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0).name("Past").build());
        bookingRepository.save(Booking.builder()
                .user(user).boat(pastBoat).session(past).status(BookingStatus.MANUAL).build());

        var active = bookingRepository.findActiveBookingsByUserId(user.getId(), LocalDate.now());
        assertTrue(active.stream().noneMatch(b -> b.getSession().getId().equals(past.getId())));
    }

    @Test
    void countActiveBookingsByDate() {
        Booking active = save(BookingStatus.MANUAL);
        save(BookingStatus.CANCELED);
        long count = bookingRepository.countActiveBookingsByDate(active.getSession().getDate());
        assertEquals(1, count);
    }

    @Test
    void findByStatusOrderByCreatedAtAsc() {
        save(BookingStatus.MANUAL);
        save(BookingStatus.CANCELLATION_REQUESTED);
        save(BookingStatus.CANCELLATION_REQUESTED);
        var pending = bookingRepository.findByStatusOrderByCreatedAtAsc(BookingStatus.CANCELLATION_REQUESTED);
        assertEquals(2, pending.size());
    }
}
