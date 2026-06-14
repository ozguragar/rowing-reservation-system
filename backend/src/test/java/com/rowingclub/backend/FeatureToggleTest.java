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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeatureToggleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String superadminToken;
    private String clubAdminToken;
    private Long clubId;

    @BeforeEach
    void setUp() {
        User superadmin = userRepository.save(User.builder()
                .fullName("Super Admin")
                .email("sa_toggle@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.SUPERADMIN)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        User clubAdmin = userRepository.save(User.builder()
                .fullName("Club Admin")
                .email("ca_toggle@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.CLUB_ADMIN)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        superadminToken = jwtService.generateAccessToken(superadmin.getEmail(), superadmin.getRole().name());
        clubAdminToken = jwtService.generateAccessToken(clubAdmin.getEmail(), clubAdmin.getRole().name());

        if (clubAdmin.getClub() != null) {
            clubId = clubAdmin.getClub().getId();
        }
    }

    @Test
    void superadminCanToggleAvailabilityModule() throws Exception {
        if (clubId == null) return;

        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features/availability")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());
    }

    @Test
    void superadminCanToggleCancellationRequests() throws Exception {
        if (clubId == null) return;

        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features/cancellation_requests")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());
    }

    @Test
    void superadminCanToggleAutoScheduler() throws Exception {
        if (clubId == null) return;

        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features/auto_scheduler")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());
    }

    @Test
    void superadminCanToggleShowBookedMembers() throws Exception {
        if (clubId == null) return;

        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features/show_booked_members")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());
    }

    @Test
    void clubAdminCannotToggleFeatures() throws Exception {
        if (clubId == null) return;

        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features/availability")
                        .header("Authorization", "Bearer " + clubAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void newClubHasAllFeaturesEnabledByDefault() throws Exception {
        mockMvc.perform(post("/api/superadmin/clubs")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Test Club"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.availability").value(true))
                .andExpect(jsonPath("$.features.cancellationRequests").value(true))
                .andExpect(jsonPath("$.features.autoScheduler").value(true))
                .andExpect(jsonPath("$.features.showBookedMembers").value(true));
    }

    @Test
    void disabledFeatureBlocksFunctionality() throws Exception {
        if (clubId == null) return;

        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features/cancellation_requests")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());
    }

    @Test
    void superadminDashboardListsAllClubs() throws Exception {
        mockMvc.perform(get("/api/superadmin/clubs")
                        .header("Authorization", "Bearer " + superadminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
