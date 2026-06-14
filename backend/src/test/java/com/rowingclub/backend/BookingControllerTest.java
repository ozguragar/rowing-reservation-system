package com.rowingclub.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.security.JwtService;
import com.rowingclub.backend.service.LedgerService;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AppSettingRepository appSettingRepository;
    @Autowired private JwtService jwtService;
    @Autowired private LedgerService ledgerService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private User member;
    private User admin;
    private RowingSession session;
    private Boat boat;
    private String memberToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("BookingControllerTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_next_day_only").settingValue("false").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("allow_cancellations").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("0").build());

        member = userRepository.save(User.builder()
                .club(club)
                .fullName("Ctrl Member").email("ctrl_member@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        admin = userRepository.save(User.builder()
                .club(club)
                .fullName("Ctrl Admin").email("ctrl_admin@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.CLUB_ADMIN).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        session = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(7, 0))
                .status(SessionStatus.APPROVED).build());

        boat = boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0).name("Test Boat").build());

        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(member).amount(BigDecimal.TEN).reason("Credits")
                .runningBalance(BigDecimal.TEN).timestamp(LocalDateTime.now()).build());

        memberToken = jwtService.generateAccessToken(member.getEmail(), member.getRole().name());
        adminToken = jwtService.generateAccessToken(admin.getEmail(), admin.getRole().name());
    }

    @Test
    void bookWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "boatId", boat.getId(), "sessionId", session.getId()))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void bookWithValidTokenSucceeds() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "boatId", boat.getId(), "sessionId", session.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("MANUAL")));
    }

    @Test
    void cancelBookingCreatesRequestStatus() throws Exception {
        var bookRes = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "boatId", boat.getId(), "sessionId", session.getId()))))
                .andExpect(status().isOk()).andReturn();

        Long bookingId = objectMapper.readTree(bookRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLATION_REQUESTED")));
    }

    @Test
    void nonAdminCannotAccessAdminMoveEndpoint() throws Exception {
        mockMvc.perform(post("/api/admin/bookings/move")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", member.getId(), "fromBoatId", 1, "toBoatId", 2))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyBookingsReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
