package com.rowingclub.backend;

import com.rowingclub.backend.dto.BookingRequest;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.service.BookingService;
import com.rowingclub.backend.service.LedgerService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Branch coverage for BookingService admin flows (book / move / remove),
 * cancellation status transitions, cox-seat validation on admin paths, and
 * the member-visibility toggles. Members here are MemberType.DEFAULT so the
 * time-of-day rules never interfere (those are covered in MemberTypeBookingTest).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingServiceFlowsTest {

    @Autowired private BookingService bookingService;
    @Autowired private LedgerService ledgerService;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private AppSettingRepository appSettingRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private User member;
    private User member2;
    private User coxUser;
    private RowingSession session;
    private RowingSession draftSession;
    private Boat boatA;
    private Boat boatB;
    private Boat coxBoat;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("Flows Club " + System.nanoTime())
                .featureAvailabilityModule(true).featureCancellationRequests(true)
                .featureAutoScheduler(true).featureShowBookedMembers(true).build());

        appSettingRepository.save(AppSetting.builder().settingKey("show_booked_members").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder().settingKey("allow_cancellations").settingValue("true").build());

        member = saveUser("flows_member@test.com", Role.MEMBER, MemberType.DEFAULT, false);
        member2 = saveUser("flows_member2@test.com", Role.MEMBER, MemberType.DEFAULT, false);
        coxUser = saveUser("flows_cox@test.com", Role.MEMBER, MemberType.DEFAULT, true);
        giveCredits(member, 10);
        giveCredits(member2, 10);
        giveCredits(coxUser, 10);

        session = sessionRepository.save(RowingSession.builder()
                .club(club).date(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
        draftSession = sessionRepository.save(RowingSession.builder()
                .club(club).date(LocalDate.now().plusDays(6))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.DRAFT).build());

        boatA = saveBoat("A 2x", 2, false);
        boatB = saveBoat("B 2x", 2, false);
        coxBoat = saveBoat("Cox 2x", 2, true);
    }

    private User saveUser(String email, Role role, MemberType type, boolean cox) {
        return userRepository.save(User.builder()
                .club(club).fullName(email).email(email)
                .passwordHash(passwordEncoder.encode("pass")).role(role).memberType(type)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).isCox(cox).build());
    }

    private void giveCredits(User u, int amount) {
        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(u).amount(BigDecimal.valueOf(amount)).reason("seed")
                .runningBalance(BigDecimal.valueOf(amount)).timestamp(LocalDateTime.now()).build());
    }

    private Boat saveBoat(String name, int cap, boolean cox) {
        return boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(cap)
                .isBasicTrainingBoat(true).hasCoxSeat(cox).currentBookings(0).name(name).build());
    }

    private BookingRequest req(Long boatId, Long sessionId, Boolean cox) {
        BookingRequest r = new BookingRequest();
        r.setBoatId(boatId);
        r.setSessionId(sessionId);
        r.setIsCoxSeat(cox);
        return r;
    }

    // ---- bookSeat edge branches ----

    @Test
    void cannotBookUnapprovedSession() {
        Boat draftBoat = boatRepository.save(Boat.builder()
                .session(draftSession).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(true).currentBookings(0).name("Draft boat").build());
        assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(member.getEmail(), req(draftBoat.getId(), draftSession.getId(), false)));
    }

    @Test
    void cannotBookBoatFromAnotherSession() {
        // boatA belongs to `session`; ask to book it under draftSession.
        assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(member.getEmail(), req(boatA.getId(), draftSession.getId(), false)));
    }

    // ---- cancellation status transitions ----

    @Test
    void cannotCancelAnotherUsersBooking() {
        var booking = bookingService.bookSeat(member.getEmail(), req(boatA.getId(), session.getId(), false));
        assertThrows(BusinessException.class,
                () -> bookingService.cancelBooking(member2.getEmail(), booking.getId()));
    }

    @Test
    void approveCancellationOnNonRequestedThrows() {
        var booking = bookingService.bookSeat(member.getEmail(), req(boatA.getId(), session.getId(), false));
        // status is MANUAL, not CANCELLATION_REQUESTED
        assertThrows(BusinessException.class, () -> bookingService.approveCancellation(booking.getId()));
    }

    @Test
    void denyCancellationOnNonRequestedThrows() {
        var booking = bookingService.bookSeat(member.getEmail(), req(boatA.getId(), session.getId(), false));
        assertThrows(BusinessException.class, () -> bookingService.denyCancellation(booking.getId()));
    }

    @Test
    void denyCancellationRestoresToManual() {
        var booking = bookingService.bookSeat(member.getEmail(), req(boatA.getId(), session.getId(), false));
        bookingService.cancelBooking(member.getEmail(), booking.getId());
        var denied = bookingService.denyCancellation(booking.getId());
        assertEquals(BookingStatus.MANUAL.name(), denied.getStatus());
    }

    // ---- adminBookUser ----

    @Test
    void adminBookRejectsDuplicateInSession() {
        bookingService.adminBookUser(member.getId(), boatA.getId(), session.getId(), false);
        assertThrows(BusinessException.class,
                () -> bookingService.adminBookUser(member.getId(), boatB.getId(), session.getId(), false));
    }

    @Test
    void adminBookRejectsFullBoat() {
        bookingService.adminBookUser(member.getId(), boatA.getId(), session.getId(), false);
        bookingService.adminBookUser(member2.getId(), boatA.getId(), session.getId(), false); // boatA cap=2 now full
        assertThrows(BusinessException.class,
                () -> bookingService.adminBookUser(coxUser.getId(), boatA.getId(), session.getId(), false));
    }

    @Test
    void adminBookCoxSeatRejectsBoatWithoutCoxSeat() {
        assertThrows(BusinessException.class,
                () -> bookingService.adminBookUser(coxUser.getId(), boatA.getId(), session.getId(), true));
    }

    @Test
    void adminBookCoxSeatRejectsNonCoxUser() {
        assertThrows(BusinessException.class,
                () -> bookingService.adminBookUser(member.getId(), coxBoat.getId(), session.getId(), true));
    }

    @Test
    void adminBookCoxSeatDoesNotDeductCredit() {
        // Drain credits then book a cox seat — cox seats are free.
        ledgerService.deductCredit(coxUser.getId(), BigDecimal.TEN, "drain");
        assertEquals(0, BigDecimal.ZERO.compareTo(ledgerService.getBalance(coxUser.getId())));
        var booking = bookingService.adminBookUser(coxUser.getId(), coxBoat.getId(), session.getId(), true);
        assertTrue(booking.getIsCoxSeat());
        assertEquals(0, BigDecimal.ZERO.compareTo(ledgerService.getBalance(coxUser.getId())));
    }

    @Test
    void adminBookCoxSeatRejectsSecondCoxOnSameBoat() {
        bookingService.adminBookUser(coxUser.getId(), coxBoat.getId(), session.getId(), true);
        User cox2 = saveUser("flows_cox2@test.com", Role.MEMBER, MemberType.DEFAULT, true);
        assertThrows(BusinessException.class,
                () -> bookingService.adminBookUser(cox2.getId(), coxBoat.getId(), session.getId(), true));
    }

    // ---- adminRemoveBooking ----

    @Test
    void adminRemoveRefundsCreditAndFreesSeat() {
        bookingService.adminBookUser(member.getId(), boatA.getId(), session.getId(), false);
        BigDecimal afterBook = ledgerService.getBalance(member.getId());
        Long bookingId = bookingRepository.findByUserId(member.getId()).get(0).getId();

        bookingService.adminRemoveBooking(bookingId);

        assertEquals(0, afterBook.add(BigDecimal.ONE).compareTo(ledgerService.getBalance(member.getId())));
        assertEquals(0, boatRepository.findById(boatA.getId()).orElseThrow().getCurrentBookings());
    }

    @Test
    void adminRemoveAlreadyCanceledThrows() {
        bookingService.adminBookUser(member.getId(), boatA.getId(), session.getId(), false);
        Long bookingId = bookingRepository.findByUserId(member.getId()).get(0).getId();
        bookingService.adminRemoveBooking(bookingId);
        assertThrows(BusinessException.class, () -> bookingService.adminRemoveBooking(bookingId));
    }

    @Test
    void adminRemoveCoxSeatDoesNotRefund() {
        bookingService.adminBookUser(coxUser.getId(), coxBoat.getId(), session.getId(), true);
        BigDecimal before = ledgerService.getBalance(coxUser.getId());
        Long bookingId = bookingRepository.findByUserId(coxUser.getId()).get(0).getId();

        bookingService.adminRemoveBooking(bookingId);

        assertEquals(0, before.compareTo(ledgerService.getBalance(coxUser.getId())));
    }

    // ---- adminMoveUser ----

    @Test
    void adminMoveWithoutSourceBookingThrows() {
        assertThrows(BusinessException.class,
                () -> bookingService.adminMoveUser(member.getId(), boatA.getId(), boatB.getId(), false));
    }

    @Test
    void adminMoveNonCoxAdjustsBoatCounts() {
        bookingService.adminBookUser(member.getId(), boatA.getId(), session.getId(), false);
        var moved = bookingService.adminMoveUser(member.getId(), boatA.getId(), boatB.getId(), false);

        assertEquals(boatB.getId(), moved.getBoatId());
        assertEquals(0, boatRepository.findById(boatA.getId()).orElseThrow().getCurrentBookings());
        assertEquals(1, boatRepository.findById(boatB.getId()).orElseThrow().getCurrentBookings());
    }

    @Test
    void adminMoveToCoxSeatRejectsNonCoxUser() {
        bookingService.adminBookUser(member.getId(), boatA.getId(), session.getId(), false);
        assertThrows(BusinessException.class,
                () -> bookingService.adminMoveUser(member.getId(), boatA.getId(), coxBoat.getId(), true));
    }

    @Test
    void adminMoveToCoxSeatOnNonCoxBoatThrows() {
        bookingService.adminBookUser(coxUser.getId(), boatA.getId(), session.getId(), false);
        assertThrows(BusinessException.class,
                () -> bookingService.adminMoveUser(coxUser.getId(), boatA.getId(), boatB.getId(), true));
    }

    @Test
    void adminMoveCoxUserIntoCoxSeatSucceeds() {
        bookingService.adminBookUser(coxUser.getId(), boatA.getId(), session.getId(), false);
        var moved = bookingService.adminMoveUser(coxUser.getId(), boatA.getId(), coxBoat.getId(), true);
        assertTrue(moved.getIsCoxSeat());
        // Leaving a rowing seat frees boatA.
        assertEquals(0, boatRepository.findById(boatA.getId()).orElseThrow().getCurrentBookings());
    }

    @Test
    void adminMoveCoxSeatToRowingSeatFillsTarget() {
        // coxUser holds the cox seat on coxBoat, then moves to a rowing seat on boatB.
        bookingService.adminBookUser(coxUser.getId(), coxBoat.getId(), session.getId(), true);
        var moved = bookingService.adminMoveUser(coxUser.getId(), coxBoat.getId(), boatB.getId(), false);
        assertFalse(moved.getIsCoxSeat());
        assertEquals(1, boatRepository.findById(boatB.getId()).orElseThrow().getCurrentBookings());
    }

    @Test
    void adminMoveToCoxSeatRejectsWhenCoxAlreadyTaken() {
        bookingService.adminBookUser(coxUser.getId(), coxBoat.getId(), session.getId(), true);
        User cox2 = saveUser("flows_cox_move@test.com", Role.MEMBER, MemberType.DEFAULT, true);
        giveCredits(cox2, 10);
        bookingService.adminBookUser(cox2.getId(), boatA.getId(), session.getId(), false);

        assertThrows(BusinessException.class,
                () -> bookingService.adminMoveUser(cox2.getId(), boatA.getId(), coxBoat.getId(), true));
    }

    // ---- visibility toggles ----

    @Test
    void showBookedMembersReflectsGlobalSetting() {
        assertTrue(bookingService.isShowBookedMembers());
        appSettingRepository.save(AppSetting.builder().settingKey("show_booked_members").settingValue("false").build());
        assertFalse(bookingService.isShowBookedMembers());
    }

    @Test
    void clubFeatureOverridesShowBookedMembersForBoat() {
        Club hidden = clubRepository.save(Club.builder()
                .name("Hidden Club " + System.nanoTime())
                .featureAvailabilityModule(true).featureCancellationRequests(true)
                .featureAutoScheduler(true).featureShowBookedMembers(false).build());
        RowingSession s = sessionRepository.save(RowingSession.builder()
                .club(hidden).date(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
        Boat b = boatRepository.save(Boat.builder()
                .session(s).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(true).currentBookings(0).name("hidden boat").build());

        // Global setting is "true" but the club has the feature off.
        assertFalse(bookingService.isShowBookedMembers(b.getId()));
    }

    // ---- read helpers ----

    @Test
    void getBookingsForBoatAndSessionReturnActive() {
        bookingService.adminBookUser(member.getId(), boatA.getId(), session.getId(), false);
        assertEquals(1, bookingService.getBookingsForBoat(boatA.getId()).size());
        assertEquals(1, bookingService.getBookingsForSession(session.getId()).size());
    }
}
