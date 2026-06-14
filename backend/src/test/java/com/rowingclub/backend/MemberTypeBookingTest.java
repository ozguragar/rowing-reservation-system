package com.rowingclub.backend;

import com.rowingclub.backend.dto.BookingRequest;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.service.BookingService;
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
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberTypeBookingTest {

    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private AppSettingRepository appSettingRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private Club club;
    private User studentMember;
    private User recreationalMember;
    private User defaultMember;
    private RowingSession tomorrowSession;
    private RowingSession futureSession;
    private Boat tomorrowBoat;
    private Boat futureBoat;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("MemberTypeBookingTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("16").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_next_day_only").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("show_booked_members").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("allow_cancellations").settingValue("true").build());

        studentMember = userRepository.save(User.builder()
                .club(club)
                .fullName("Student Member")
                .email("student_member@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER)
                .memberType(MemberType.STUDENT)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(10)
                .build());

        recreationalMember = userRepository.save(User.builder()
                .club(club)
                .fullName("Recreational Member")
                .email("recreational_member@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER)
                .memberType(MemberType.RECREATIONAL)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(5)
                .build());

        defaultMember = userRepository.save(User.builder()
                .club(club)
                .fullName("Default Member")
                .email("default_member@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER)
                .memberType(MemberType.DEFAULT)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(5)
                .build());

        tomorrowSession = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now(IST).plusDays(1))
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(18, 0))
                .status(SessionStatus.APPROVED)
                .build());

        futureSession = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now(IST).plusDays(5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status(SessionStatus.APPROVED)
                .build());

        tomorrowBoat = boatRepository.save(Boat.builder()
                .session(tomorrowSession)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .name("Tomorrow 4x")
                .build());

        futureBoat = boatRepository.save(Boat.builder()
                .session(futureSession)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .name("Future 4x")
                .build());

        for (User u : new User[]{studentMember, recreationalMember, defaultMember}) {
            ledgerRepository.save(FinancialLedger.builder()
                    .club(club).user(u).amount(BigDecimal.TEN)
                    .reason("Test credits").runningBalance(BigDecimal.TEN)
                    .timestamp(LocalDateTime.now()).build());
        }
    }

    @Test
    void memberTypeAttributeExists() {
        User user = userRepository.findByEmail("student_member@test.com").orElseThrow();
        assertNotNull(user.getMemberType(), "Member should have a memberType attribute");
    }

    @Test
    void memberTypeEnumValues() {
        assertDoesNotThrow(() -> MemberType.valueOf("STUDENT"));
        assertDoesNotThrow(() -> MemberType.valueOf("RECREATIONAL"));
        assertDoesNotThrow(() -> MemberType.valueOf("DEFAULT"));
    }

    @Test
    void studentMemberRestrictedToNextDayWhenToggleOn() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(futureBoat.getId());
        request.setSessionId(futureSession.getId());

        assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(studentMember.getEmail(), request),
                "STUDENT member should be restricted to next-day sessions when toggle is on");
    }

    @Test
    void studentMemberCanBookAfter1600() {
        LocalTime nowIst = LocalTime.now(IST);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                nowIst.isAfter(LocalTime.of(16, 0)),
                "Skipping - test requires Istanbul time after 16:00");

        BookingRequest request = new BookingRequest();
        request.setBoatId(tomorrowBoat.getId());
        request.setSessionId(tomorrowSession.getId());

        var booking = bookingService.bookSeat(studentMember.getEmail(), request);
        assertNotNull(booking);
    }

    @Test
    void studentMemberCannotBookBefore1600() {
        LocalTime nowIst = LocalTime.now(IST);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                nowIst.isBefore(LocalTime.of(16, 0)),
                "Skipping - test requires Istanbul time before 16:00");

        BookingRequest request = new BookingRequest();
        request.setBoatId(tomorrowBoat.getId());
        request.setSessionId(tomorrowSession.getId());

        assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(studentMember.getEmail(), request),
                "STUDENT member should not be able to book before 16:00");
    }

    @Test
    void recreationalMemberCanBookAnytime() {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(futureBoat.getId());
        request.setSessionId(futureSession.getId());

        var booking = bookingService.bookSeat(recreationalMember.getEmail(), request);
        assertNotNull(booking, "RECREATIONAL member should be able to book at any time");
    }

    @Test
    void recreationalMemberBlockedFromTomorrowAfterCutoff() {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(tomorrowBoat.getId());
        request.setSessionId(tomorrowSession.getId());

        assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(recreationalMember.getEmail(), request),
                "RECREATIONAL member should be blocked from tomorrow's sessions after cutoff");
    }

    @Test
    void defaultMemberHasNoTimeRestrictions() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(futureBoat.getId());
        request.setSessionId(futureSession.getId());

        var booking = bookingService.bookSeat(defaultMember.getEmail(), request);
        assertNotNull(booking, "DEFAULT member should have no time restrictions");
    }

    @Test
    void defaultMemberCanBookTomorrowRegardlessOfCutoff() {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(tomorrowBoat.getId());
        request.setSessionId(tomorrowSession.getId());

        var booking = bookingService.bookSeat(defaultMember.getEmail(), request);
        assertNotNull(booking, "DEFAULT member should be able to book tomorrow regardless of cutoff");
    }

    @Test
    void studentMemberCanBookNextDayWhenToggleOff() {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_next_day_only").settingValue("false").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(futureBoat.getId());
        request.setSessionId(futureSession.getId());

        var booking = bookingService.bookSeat(studentMember.getEmail(), request);
        assertNotNull(booking, "STUDENT member should be able to book non-next-day sessions when toggle is off");
    }
}
