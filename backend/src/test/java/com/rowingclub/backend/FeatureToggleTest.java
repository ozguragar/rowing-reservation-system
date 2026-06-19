package com.rowingclub.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.security.JwtService;
import com.rowingclub.backend.service.ClubService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises the real superadmin club-feature API:
 *   GET  /api/superadmin/clubs
 *   PUT  /api/superadmin/clubs/{clubId}/features   (body: UpdateClubFeaturesRequest)
 * plus the default-feature behaviour of {@link ClubService#createClub}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeatureToggleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private ClubService clubService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String superadminToken;
    private String clubAdminToken;
    private Long clubId;

    @BeforeEach
    void setUp() {
        Club club = clubRepository.save(Club.builder()
                .name("FeatureToggle Club " + System.nanoTime())
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        clubId = club.getId();

        User superadmin = userRepository.save(User.builder()
                .fullName("Super Admin").email("sa_toggle@test.com")
                .passwordHash(passwordEncoder.encode("pass")).role(Role.SUPERADMIN)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());

        User clubAdmin = userRepository.save(User.builder()
                .club(club)
                .fullName("Club Admin").email("ca_toggle@test.com")
                .passwordHash(passwordEncoder.encode("pass")).role(Role.CLUB_ADMIN)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());

        superadminToken = jwtService.generateAccessToken(superadmin.getEmail(), superadmin.getRole().name());
        clubAdminToken = jwtService.generateAccessToken(clubAdmin.getEmail(), clubAdmin.getRole().name());
    }

    @Test
    void superadminCanDisableAvailabilityModule() throws Exception {
        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("featureAvailabilityModule", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureAvailabilityModule").value(false));
    }

    @Test
    void superadminCanDisableCancellationRequests() throws Exception {
        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("featureCancellationRequests", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureCancellationRequests").value(false));
    }

    @Test
    void superadminCanDisableAutoScheduler() throws Exception {
        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("featureAutoScheduler", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureAutoScheduler").value(false));
    }

    @Test
    void superadminCanDisableShowBookedMembers() throws Exception {
        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("featureShowBookedMembers", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureShowBookedMembers").value(false));
    }

    @Test
    void onlyTheTargetedFeatureChanges() throws Exception {
        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("featureAutoScheduler", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureAutoScheduler").value(false))
                .andExpect(jsonPath("$.featureAvailabilityModule").value(true))
                .andExpect(jsonPath("$.featureCancellationRequests").value(true))
                .andExpect(jsonPath("$.featureShowBookedMembers").value(true));
    }

    @Test
    void clubAdminCannotToggleFeatures() throws Exception {
        mockMvc.perform(put("/api/superadmin/clubs/" + clubId + "/features")
                        .header("Authorization", "Bearer " + clubAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("featureAvailabilityModule", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void newClubHasAllFeaturesEnabledByDefault() {
        Club created = clubService.createClub("Brand New Club " + System.nanoTime());
        assertTrue(created.getFeatureAvailabilityModule());
        assertTrue(created.getFeatureCancellationRequests());
        assertTrue(created.getFeatureAutoScheduler());
        assertTrue(created.getFeatureShowBookedMembers());
    }

    @Test
    void superadminDashboardListsAllClubs() throws Exception {
        mockMvc.perform(get("/api/superadmin/clubs")
                        .header("Authorization", "Bearer " + superadminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
