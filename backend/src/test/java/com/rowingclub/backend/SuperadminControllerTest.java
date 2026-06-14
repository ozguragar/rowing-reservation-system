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
class SuperadminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String superadminToken;
    private String clubAdminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        User superadmin = userRepository.save(User.builder()
                .fullName("Super Admin")
                .email("sa_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.SUPERADMIN)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        User clubAdmin = userRepository.save(User.builder()
                .fullName("Club Admin")
                .email("ca_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.CLUB_ADMIN)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        User member = userRepository.save(User.builder()
                .fullName("Member")
                .email("member_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.MEMBER)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        superadminToken = jwtService.generateAccessToken(superadmin.getEmail(), superadmin.getRole().name());
        clubAdminToken = jwtService.generateAccessToken(clubAdmin.getEmail(), clubAdmin.getRole().name());
        memberToken = jwtService.generateAccessToken(member.getEmail(), member.getRole().name());
    }

    @Test
    void superadminCanAccessSuperadminDashboard() throws Exception {
        mockMvc.perform(get("/api/superadmin/clubs")
                        .header("Authorization", "Bearer " + superadminToken))
                .andExpect(status().isOk());
    }

    @Test
    void clubAdminCannotAccessSuperadminEndpoints() throws Exception {
        mockMvc.perform(get("/api/superadmin/clubs")
                        .header("Authorization", "Bearer " + clubAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotAccessSuperadminEndpoints() throws Exception {
        mockMvc.perform(get("/api/superadmin/clubs")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessSuperadminEndpoints() throws Exception {
        mockMvc.perform(get("/api/superadmin/clubs"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void superadminCanModerateAnyClub() throws Exception {
        mockMvc.perform(get("/api/superadmin/clubs")
                        .header("Authorization", "Bearer " + superadminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void superadminCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + superadminToken))
                .andExpect(status().isOk());
    }

    @Test
    void superadminCanChangeOwnPassword() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .header("Authorization", "Bearer " + superadminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "pass",
                                "newPassword", "newSecurePass123"))))
                .andExpect(status().isOk());
    }
}
