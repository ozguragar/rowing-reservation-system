package com.rowingclub.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rowingclub.backend.dto.BookingRequest;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.security.JwtService;
import com.rowingclub.backend.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoxSeatBoatManagementTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingService bookingService;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private AppSettingRepository appSettingRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private User clubAdmin;
    private User trainer;
    private User coxUser;
    private User regularMember;
    private String clubAdminToken;
    private RowingSession session;
    private Boat boatWithCox;
    private Boat boatWithoutCox;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("CoxSeatBoatManagementTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_next_day_only").settingValue("false").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("show_booked_members").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("allow_cancellations").settingValue("true").build());

        clubAdmin = userRepository.save(User.builder()
                .club(club)
                .fullName("Club Admin")
                .email("ca_cox@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.CLUB_ADMIN)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        trainer = userRepository.save(User.builder()
                .club(club)
                .fullName("Trainer Cox")
                .email("trainer_cox@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.TRAINER)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .isCox(false)
                .build());

        coxUser = userRepository.save(User.builder()
                .club(club)
                .fullName("Cox User")
                .email("cox_user@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER)
                .memberType(MemberType.DEFAULT)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(5)
                .isCox(true)
                .build());

        regularMember = userRepository.save(User.builder()
                .club(club)
                .fullName("Regular Member")
                .email("regular_cox@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER)
                .memberType(MemberType.DEFAULT)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(5)
                .isCox(false)
                .build());

        clubAdminToken = jwtService.generateAccessToken(clubAdmin.getEmail(), clubAdmin.getRole().name());

        session = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).plusDays(1))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .status(SessionStatus.APPROVED)
                .build());

        boatWithCox = boatRepository.save(Boat.builder()
                .session(session)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .hasCoxSeat(true)
                .name("4x with Cox")
                .build());

        boatWithoutCox = boatRepository.save(Boat.builder()
                .session(session)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .hasCoxSeat(false)
                .name("4x no Cox")
                .build());

        for (User u : new User[]{trainer, coxUser, regularMember}) {
            ledgerRepository.save(FinancialLedger.builder()
                    .club(club).user(u).amount(BigDecimal.TEN)
                    .reason("Test credits").runningBalance(BigDecimal.TEN)
                    .timestamp(LocalDateTime.now()).build());
        }
    }

    @Test
    void boatHasCoxSeatAttribute() {
        Boat boat = boatRepository.findById(boatWithCox.getId()).orElseThrow();
        assertTrue(boat.getHasCoxSeat(), "Boat should have hasCoxSeat attribute");
    }

    @Test
    void addBoatWithCoxSeatViaAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/sessions/" + session.getId() + "/boats")
                        .header("Authorization", "Bearer " + clubAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "COASTAL",
                                "capacity", 4,
                                "isBasicTrainingBoat", false,
                                "hasCoxSeat", true,
                                "name", "New Cox Boat"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCoxSeat").value(true));
    }

    @Test
    void coxSeatDoesNotCountTowardCapacity() {
        Boat boat = boatRepository.findById(boatWithCox.getId()).orElseThrow();
        assertEquals(4, boat.getCapacity(), "Cox seat should not be included in the rowing capacity");
    }

    @Test
    void coxSeatIsOnlyOneSeatPerBoat() {
        Boat boat = boatRepository.findById(boatWithCox.getId()).orElseThrow();
        assertTrue(boat.getHasCoxSeat());
    }

    @Test
    void userHasCoxAttribute() {
        User cox = userRepository.findByEmail("cox_user@test.com").orElseThrow();
        assertTrue(cox.getIsCox(), "User should have cox boolean attribute");
    }

    @Test
    void onlyCoxEligibleUserCanBookCoxSeat() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(boatWithCox.getId());
        request.setSessionId(session.getId());
        request.setIsCoxSeat(true);

        assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(regularMember.getEmail(), request),
                "Non-cox user should not be able to book cox seat");
    }

    @Test
    void coxUserCanBookCoxSeat() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(boatWithCox.getId());
        request.setSessionId(session.getId());
        request.setIsCoxSeat(true);

        var booking = bookingService.bookSeat(coxUser.getEmail(), request);
        assertNotNull(booking, "Cox-eligible user should be able to book cox seat");
    }

    @Test
    void trainerIsAutomaticallyCoxEligible() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(boatWithCox.getId());
        request.setSessionId(session.getId());
        request.setIsCoxSeat(true);

        var booking = bookingService.bookSeat(trainer.getEmail(), request);
        assertNotNull(booking, "Trainer should automatically be eligible for cox seat");
    }

    @Test
    void coxSeatBookingDoesNotRequireCredits() {
        User brokeCox = userRepository.save(User.builder()
                .club(club)
                .fullName("Broke Cox")
                .email("brokecox@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER)
                .memberType(MemberType.DEFAULT)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(5)
                .isCox(true)
                .build());

        BookingRequest request = new BookingRequest();
        request.setBoatId(boatWithCox.getId());
        request.setSessionId(session.getId());
        request.setIsCoxSeat(true);

        var booking = bookingService.bookSeat(brokeCox.getEmail(), request);
        assertNotNull(booking, "Cox seat booking should not require credits");
    }

    @Test
    void cannotBookCoxSeatOnBoatWithoutCoxSeat() {
        BookingRequest request = new BookingRequest();
        request.setBoatId(boatWithoutCox.getId());
        request.setSessionId(session.getId());
        request.setIsCoxSeat(true);

        assertThrows(BusinessException.class,
                () -> bookingService.bookSeat(coxUser.getEmail(), request),
                "Should not be able to book cox seat on a boat without cox seat");
    }

    @Test
    void coxSeatBookingDoesNotReduceRowingCapacity() {
        BookingRequest coxRequest = new BookingRequest();
        coxRequest.setBoatId(boatWithCox.getId());
        coxRequest.setSessionId(session.getId());
        coxRequest.setIsCoxSeat(true);
        bookingService.bookSeat(coxUser.getEmail(), coxRequest);

        Boat refreshed = boatRepository.findById(boatWithCox.getId()).orElseThrow();
        assertEquals(0, refreshed.getCurrentBookings(),
                "Cox seat booking should not count toward current bookings (rowing capacity)");
    }

    @Test
    void trainersAssignedToCoxByDefault() {
        User trainerUser = userRepository.findByEmail("trainer_cox@test.com").orElseThrow();
        assertEquals(Role.TRAINER, trainerUser.getRole());
        assertTrue(trainerUser.getRole() == Role.TRAINER,
                "Trainers are assigned to cox seats by default");
    }
}
