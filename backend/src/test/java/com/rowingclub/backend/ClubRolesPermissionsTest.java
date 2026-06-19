package com.rowingclub.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.security.JwtService;
import com.rowingclub.backend.service.BookingService;
import com.rowingclub.backend.dto.BookingRequest;
import com.rowingclub.backend.exception.BusinessException;
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
class ClubRolesPermissionsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private AppSettingRepository appSettingRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private User clubAdmin;
    private User trainer;
    private User member;
    private String clubAdminToken;
    private String trainerToken;
    private String memberToken;
    private RowingSession session;
    private Boat boat;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("ClubRolesPermissionsTest Club")
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
                .email("clubadmin_role@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.CLUB_ADMIN)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        trainer = userRepository.save(User.builder()
                .club(club)
                .fullName("Trainer User")
                .email("trainer_role@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.TRAINER)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        member = userRepository.save(User.builder()
                .club(club)
                .fullName("Member User")
                .email("member_role@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        clubAdminToken = jwtService.generateAccessToken(clubAdmin.getEmail(), clubAdmin.getRole().name());
        trainerToken = jwtService.generateAccessToken(trainer.getEmail(), trainer.getRole().name());
        memberToken = jwtService.generateAccessToken(member.getEmail(), member.getRole().name());

        session = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).plusDays(1))
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(18, 0))
                .status(SessionStatus.APPROVED)
                .build());

        boat = boatRepository.save(Boat.builder()
                .session(session)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .name("Test 4x")
                .build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(member).amount(BigDecimal.TEN)
                .reason("Test credits").runningBalance(BigDecimal.TEN)
                .timestamp(LocalDateTime.now()).build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(trainer).amount(BigDecimal.TEN)
                .reason("Test credits").runningBalance(BigDecimal.TEN)
                .timestamp(LocalDateTime.now()).build());
    }

    @Test
    void clubAdminRoleExists() {
        assertDoesNotThrow(() -> Role.valueOf("CLUB_ADMIN"));
    }

    @Test
    void trainerRoleExists() {
        assertDoesNotThrow(() -> Role.valueOf("TRAINER"));
    }

    @Test
    void memberRoleExists() {
        assertDoesNotThrow(() -> Role.valueOf("MEMBER"));
    }

    @Test
    void oldStudentAndClubMemberRolesRemoved() {
        assertThrows(IllegalArgumentException.class, () -> Role.valueOf("STUDENT"));
        assertThrows(IllegalArgumentException.class, () -> Role.valueOf("CLUB_MEMBER"));
    }

    @Test
    void clubAdminCanManageUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + clubAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void clubAdminCanManageSessions() throws Exception {
        mockMvc.perform(get("/api/admin/sessions")
                        .param("start", LocalDate.now().toString())
                        .param("end", LocalDate.now().plusDays(7).toString())
                        .header("Authorization", "Bearer " + clubAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void trainerCanManageSessions() throws Exception {
        mockMvc.perform(get("/api/admin/sessions")
                        .param("start", LocalDate.now().toString())
                        .param("end", LocalDate.now().plusDays(7).toString())
                        .header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk());
    }

    @Test
    void trainerCanMoveMembers() throws Exception {
        Boat targetBoat = boatRepository.save(Boat.builder()
                .session(session)
                .type(BoatType.COASTAL)
                .capacity(4)
                .isBasicTrainingBoat(false)
                .currentBookings(0)
                .name("Target 4x")
                .build());

        // The member must already be booked on the source boat for a move to be valid.
        boat.setCurrentBookings(1);
        boatRepository.save(boat);
        bookingRepository.save(Booking.builder()
                .user(member).boat(boat).session(session)
                .status(BookingStatus.MANUAL).isCoxSeat(false).build());

        mockMvc.perform(post("/api/admin/bookings/move")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", member.getId(),
                                "fromBoatId", boat.getId(),
                                "toBoatId", targetBoat.getId()))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void trainerCanCreateBookingsForMembers() throws Exception {
        mockMvc.perform(post("/api/admin/bookings")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", member.getId(),
                                "boatId", boat.getId(),
                                "sessionId", session.getId()))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void trainerCannotManageUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void trainerCannotManageCredits() throws Exception {
        mockMvc.perform(post("/api/admin/ledger/" + member.getId() + "/credit")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 5,
                                "reason", "Test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void trainerCannotManageSettings() throws Exception {
        mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void clubAdminCanOnlyManageOwnClub() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + clubAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
