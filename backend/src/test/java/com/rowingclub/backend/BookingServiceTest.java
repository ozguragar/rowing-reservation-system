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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingServiceTest {

    @Autowired private BookingService bookingService;
    @Autowired private LedgerService ledgerService;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private AppSettingRepository appSettingRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private User beginnerUser;
    private User trainedUser;
    private RowingSession session;
    private Boat advancedBoat;
    private Boat basicBoat;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("BookingServiceTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("show_booked_members").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_next_day_only").settingValue("false").build());
        // hour=0 means students can always book (any time is "after midnight")
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("allow_cancellations").settingValue("true").build());

        beginnerUser = userRepository.save(User.builder()
                .club(club)
                .fullName("Beginner User")
                .email("beginner@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER)
                .isFinishedBasicTraining(false)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        trainedUser = userRepository.save(User.builder()
                .club(club)
                .fullName("Trained User")
                .email("trained@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(10)
                .build());

        session = sessionRepository.save(RowingSession.builder()
                .club(club)
                // Use Istanbul-tomorrow so role-based date checks (which compute
                // tomorrow against Europe/Istanbul) see this fixture as "tomorrow"
                // regardless of the JVM's default timezone.
                .date(LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).plusDays(1))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED)
                .build());

        advancedBoat = boatRepository.save(Boat.builder()
                .session(session)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .name("Advanced 4x")
                .build());

        basicBoat = boatRepository.save(Boat.builder()
                .session(session)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(true)
                .currentBookings(0)
                .name("Basic Training 4x")
                .build());

        // Give both users credits
        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(beginnerUser).amount(BigDecimal.TEN)
                .reason("Test credits").runningBalance(BigDecimal.TEN)
                .timestamp(LocalDateTime.now()).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(trainedUser).amount(BigDecimal.TEN)
                .reason("Test credits").runningBalance(BigDecimal.TEN)
                .timestamp(LocalDateTime.now()).build());
    }

    @Test
    void getBookingsForUserReturnsHistoryWithSessionDate() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(basicBoat.getId());
        request.setSessionId(session.getId());
        bookingService.bookSeat(trainedUser.getEmail(), request);

        var history = bookingService.getBookingsForUser(trainedUser.getId());

        assertEquals(1, history.size());
        var dto = history.get(0);
        assertEquals(session.getDate(), dto.getSessionDate());
        assertEquals(LocalTime.of(8, 0), dto.getSessionStartTime());
        assertEquals("Basic Training 4x", dto.getBoatName());
    }

    @Test
    void getBookingsForUserExcludesCanceled() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(basicBoat.getId());
        request.setSessionId(session.getId());
        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);
        bookingService.adminRemoveBooking(booking.getId());

        assertTrue(bookingService.getBookingsForUser(trainedUser.getId()).isEmpty());
    }

    @Test
    void beginnerCannotBookAdvancedBoat() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                bookingService.bookSeat(beginnerUser.getEmail(), request));

        assertEquals("You must complete basic training before booking advanced boats", ex.getMessage());
    }

    @Test
    void beginnerCanBookBasicTrainingBoat() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(basicBoat.getId());
        request.setSessionId(session.getId());

        var booking = bookingService.bookSeat(beginnerUser.getEmail(), request);

        assertNotNull(booking);
        assertEquals(BookingStatus.MANUAL.name(), booking.getStatus());
    }

    @Test
    void trainedUserCanBookAdvancedBoat() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());

        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);

        assertNotNull(booking);
        assertEquals(advancedBoat.getId(), booking.getBoatId());
    }

    @Test
    void cannotBookFullBoat() {
        Boat fullBoat = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(1)
                .isBasicTrainingBoat(true).currentBookings(1).name("Full 1x").build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(fullBoat.getId());
        request.setSessionId(session.getId());

        assertThrows(BusinessException.class, () ->
                bookingService.bookSeat(beginnerUser.getEmail(), request));
    }

    @Test
    void cannotBookWithZeroCredits() {
        User brokeUser = userRepository.save(User.builder()
                .club(club)
                .fullName("Broke User").email("broke@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(brokeUser).amount(BigDecimal.ZERO)
                .reason("No credits").runningBalance(BigDecimal.ZERO)
                .timestamp(LocalDateTime.now()).build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());

        assertThrows(BusinessException.class, () ->
                bookingService.bookSeat(brokeUser.getEmail(), request));
    }

    @Test
    void cannotDoubleBookSameSession() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(basicBoat.getId());
        request.setSessionId(session.getId());

        bookingService.bookSeat(beginnerUser.getEmail(), request);

        assertThrows(BusinessException.class, () ->
                bookingService.bookSeat(beginnerUser.getEmail(), request));
    }

    @Test
    void bookingDeductsOneCredit() {
        BigDecimal before = ledgerService.getBalance(trainedUser.getId());

        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());
        bookingService.bookSeat(trainedUser.getEmail(), request);

        BigDecimal after = ledgerService.getBalance(trainedUser.getId());
        assertEquals(before.subtract(BigDecimal.ONE).compareTo(after), 0);
    }

    @Test
    void cancelBookingCreatesPendingRequest() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());
        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);

        BigDecimal afterBook = ledgerService.getBalance(trainedUser.getId());
        var dto = bookingService.cancelBooking(trainedUser.getEmail(), booking.getId());
        BigDecimal afterRequest = ledgerService.getBalance(trainedUser.getId());

        assertEquals(BookingStatus.CANCELLATION_REQUESTED.name(), dto.getStatus());
        // Credit NOT refunded yet
        assertEquals(0, afterBook.compareTo(afterRequest));
    }

    @Test
    void approveCancellationRefundsCreditAndFreesSeat() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());
        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);
        bookingService.cancelBooking(trainedUser.getEmail(), booking.getId());

        BigDecimal before = ledgerService.getBalance(trainedUser.getId());
        bookingService.approveCancellation(booking.getId());
        BigDecimal after = ledgerService.getBalance(trainedUser.getId());

        assertEquals(0, before.add(BigDecimal.ONE).compareTo(after));
        Boat refreshed = boatRepository.findById(advancedBoat.getId()).orElseThrow();
        assertEquals(0, refreshed.getCurrentBookings());
    }

    @Test
    void denyCancellationRestoresBookingStatus() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());
        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);
        bookingService.cancelBooking(trainedUser.getEmail(), booking.getId());

        BigDecimal before = ledgerService.getBalance(trainedUser.getId());
        var denied = bookingService.denyCancellation(booking.getId());
        BigDecimal after = ledgerService.getBalance(trainedUser.getId());

        assertEquals(BookingStatus.MANUAL.name(), denied.getStatus());
        // No refund on deny
        assertEquals(0, before.compareTo(after));
    }

    @Test
    void doubleCancelThrows() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());
        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);
        bookingService.cancelBooking(trainedUser.getEmail(), booking.getId());

        assertThrows(BusinessException.class,
                () -> bookingService.cancelBooking(trainedUser.getEmail(), booking.getId()));
    }

    @Test
    void cancellationsDisabledThrows() {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("allow_cancellations").settingValue("false").build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());
        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);

        assertThrows(BusinessException.class,
                () -> bookingService.cancelBooking(trainedUser.getEmail(), booking.getId()));
    }

    @Test
    void seatHeldDuringCancellationRequest() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(advancedBoat.getId());
        request.setSessionId(session.getId());
        var booking = bookingService.bookSeat(trainedUser.getEmail(), request);
        bookingService.cancelBooking(trainedUser.getEmail(), booking.getId());

        Boat boat = boatRepository.findById(advancedBoat.getId()).orElseThrow();
        assertEquals(1, boat.getCurrentBookings()); // seat still held
    }

    @Test
    void adminBookUserRequiresCredit() {
        User brokeUser = userRepository.save(User.builder()
                .club(club)
                .fullName("No Credit").email("nocredit@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        assertThrows(BusinessException.class,
                () -> bookingService.adminBookUser(brokeUser.getId(), basicBoat.getId(), session.getId(), false));
    }

    @Test
    void adminMoveUserToFullBoatThrows() {
        Boat fullBoat = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(1)
                .isBasicTrainingBoat(true).currentBookings(1).name("Full Boat").build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(basicBoat.getId());
        request.setSessionId(session.getId());
        bookingService.bookSeat(beginnerUser.getEmail(), request);

        assertThrows(BusinessException.class,
                () -> bookingService.adminMoveUser(beginnerUser.getId(), basicBoat.getId(), fullBoat.getId(), false));
    }

    @Test
    void getPendingCancellationsReturnsCancellationRequestedOnly() {
        BookingRequest r1 = new BookingRequest();
        r1.setBoatId(advancedBoat.getId());
        r1.setSessionId(session.getId());
        var b1 = bookingService.bookSeat(trainedUser.getEmail(), r1);
        bookingService.cancelBooking(trainedUser.getEmail(), b1.getId());

        BookingRequest r2 = new BookingRequest();
        r2.setBoatId(basicBoat.getId());
        r2.setSessionId(session.getId());
        bookingService.bookSeat(beginnerUser.getEmail(), r2); // not cancelled

        List<com.rowingclub.backend.dto.BookingDto> pending = bookingService.getPendingCancellations();
        assertEquals(1, pending.size());
        assertEquals(BookingStatus.CANCELLATION_REQUESTED.name(), pending.get(0).getStatus());
    }

    @Test
    void bookingHourDisabledSkipsAllChecks() {
        // Force hour=23 → normally both roles would be restricted outside narrow windows.
        // Add a CLUB_MEMBER who would otherwise be blocked by the new next-day rule before hour.
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("23").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("booking_hour_disabled").settingValue("true").build());

        User member = userRepository.save(User.builder()
                .club(club)
                .fullName("Bypass Member").email("bypass_mem@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(member).amount(BigDecimal.TEN).reason("Credits")
                .runningBalance(BigDecimal.TEN).timestamp(LocalDateTime.now()).build());

        // Session is >1 day out — would normally be blocked for member before hour
        RowingSession futureSession = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
        Boat futureBoat = boatRepository.save(Boat.builder()
                .session(futureSession).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0).name("Future").build());

        BookingRequest req = new BookingRequest();
        req.setBoatId(futureBoat.getId());
        req.setSessionId(futureSession.getId());
        // Expect success because booking hour is disabled
        var dto = bookingService.bookSeat(member.getEmail(), req);
        assertNotNull(dto);
    }

    @Test
    void memberBeforeHourCanBookFarFutureSession() {
        // Members have no time-of-day restriction — even with cutoff set to 23,
        // a member can book a session several days ahead.
        java.time.LocalTime nowIst = java.time.LocalTime.now(java.time.ZoneId.of("Europe/Istanbul"));
        org.junit.jupiter.api.Assumptions.assumeTrue(
                nowIst.isBefore(java.time.LocalTime.of(23, 0)),
                "Skipping — test requires Istanbul time before 23:00");
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("23").build());

        User member = userRepository.save(User.builder()
                .club(club)
                .fullName("Before Hour Member").email("before_mem@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(member).amount(BigDecimal.TEN).reason("Credits")
                .runningBalance(BigDecimal.TEN).timestamp(LocalDateTime.now()).build());

        RowingSession futureSession = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
        Boat futureBoat = boatRepository.save(Boat.builder()
                .session(futureSession).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0).name("FutureB").build());

        BookingRequest req = new BookingRequest();
        req.setBoatId(futureBoat.getId());
        req.setSessionId(futureSession.getId());
        var dto = bookingService.bookSeat(member.getEmail(), req);
        assertNotNull(dto);
    }

    @Test
    void memberBeforeHourCanBookNextDaySession() {
        java.time.ZoneId ist = java.time.ZoneId.of("Europe/Istanbul");
        java.time.LocalTime nowIst = java.time.LocalTime.now(ist);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                nowIst.isBefore(java.time.LocalTime.of(23, 0)),
                "Skipping — test requires Istanbul time before 23:00");
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("23").build());

        User member = userRepository.save(User.builder()
                .club(club)
                .fullName("NextDay Member").email("nextday_mem@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(member).amount(BigDecimal.TEN).reason("Credits")
                .runningBalance(BigDecimal.TEN).timestamp(LocalDateTime.now()).build());

        BookingRequest req = new BookingRequest();
        req.setBoatId(basicBoat.getId());
        req.setSessionId(session.getId());
        var dto = bookingService.bookSeat(member.getEmail(), req);
        assertNotNull(dto);
    }

    @Test
    void memberAfterHourCannotBookTomorrowSession() {
        // After the cutoff, tomorrow's sessions are reserved for students —
        // members must wait until the day of (or book further-out sessions).
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());

        // The "tomorrow reserved for students" rule applies to RECREATIONAL members;
        // a DEFAULT member has no time-of-day restriction.
        User member = userRepository.save(User.builder()
                .club(club)
                .fullName("After Hour Member").email("after_tomorrow_mem@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).memberType(MemberType.RECREATIONAL).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(member).amount(BigDecimal.TEN).reason("Credits")
                .runningBalance(BigDecimal.TEN).timestamp(LocalDateTime.now()).build());

        // Session fixture is for tomorrow; member after cutoff must be rejected
        BookingRequest req = new BookingRequest();
        req.setBoatId(basicBoat.getId());
        req.setSessionId(session.getId());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(member.getEmail(), req));
        assertTrue(ex.getMessage().toLowerCase().contains("reserved for students"));
    }

    @Test
    void memberAfterHourCanBookAnyDay() {
        // Force "after hour" by setting hour=0 — any time >= 00:00
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());

        User member = userRepository.save(User.builder()
                .club(club)
                .fullName("After Member").email("after_mem@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(member).amount(BigDecimal.TEN).reason("Credits")
                .runningBalance(BigDecimal.TEN).timestamp(LocalDateTime.now()).build());

        RowingSession futureSession = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().plusDays(5))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED).build());
        Boat futureBoat = boatRepository.save(Boat.builder()
                .session(futureSession).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0).name("AfterHrBoat").build());

        BookingRequest req = new BookingRequest();
        req.setBoatId(futureBoat.getId());
        req.setSessionId(futureSession.getId());
        var dto = bookingService.bookSeat(member.getEmail(), req);
        assertNotNull(dto);
    }
}
