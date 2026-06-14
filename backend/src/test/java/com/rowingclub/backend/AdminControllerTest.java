package com.rowingclub.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.security.JwtService;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubRepository clubRepository;

    private Club club;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("AdminControllerTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        User admin = userRepository.save(User.builder()
                .club(club)
                .fullName("Admin").email("admin_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.CLUB_ADMIN).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        User member = userRepository.save(User.builder()
                .club(club)
                .fullName("Member").email("member_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        adminToken = jwtService.generateAccessToken(admin.getEmail(), admin.getRole().name());
        memberToken = jwtService.generateAccessToken(member.getEmail(), member.getRole().name());
    }

    @Test
    void nonAdminGets403OnAdminSessions() throws Exception {
        mockMvc.perform(get("/api/admin/sessions")
                        .param("start", LocalDate.now().toString())
                        .param("end", LocalDate.now().plusDays(1).toString())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetSessions() throws Exception {
        mockMvc.perform(get("/api/admin/sessions")
                        .param("start", LocalDate.now().toString())
                        .param("end", LocalDate.now().plusDays(7).toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanCreateSession() throws Exception {
        mockMvc.perform(post("/api/admin/sessions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "date", LocalDate.now().plusDays(5).toString(),
                                "startTime", "08:00:00",
                                "endTime", "09:00:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void adminCanGetAllUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanGetAnalytics() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/occupancy")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanGetCancellationRequests() throws Exception {
        mockMvc.perform(get("/api/admin/cancellation-requests")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanGetAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanGetSettings() throws Exception {
        mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanBulkApprove() throws Exception {
        RowingSession s = sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .status(SessionStatus.DRAFT).build());

        mockMvc.perform(post("/api/admin/sessions/bulk-approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(s.getId()))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedGets401OnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void setBasicTrainingRequiresAdmin() throws Exception {
        User other = userRepository.save(User.builder()
                .fullName("Other").email("bt_other@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(com.rowingclub.backend.enums.Role.MEMBER)
                .isFinishedBasicTraining(false)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        mockMvc.perform(patch("/api/admin/users/" + other.getId() + "/basic-training")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("finished", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void setBasicTrainingAsAdminFlipsFlag() throws Exception {
        User other = userRepository.save(User.builder()
                .fullName("Flip User").email("bt_flip@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(com.rowingclub.backend.enums.Role.MEMBER)
                .isFinishedBasicTraining(false)
                .isOnSchoolTeam(false).lessonsAttended(0).build());

        mockMvc.perform(patch("/api/admin/users/" + other.getId() + "/basic-training")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("finished", true))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.isFinishedBasicTraining").value(true));
    }
}
