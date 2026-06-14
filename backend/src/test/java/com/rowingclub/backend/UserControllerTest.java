package com.rowingclub.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() {
        User u = userRepository.save(User.builder()
                .fullName("UC User").email("ucuser@test.com")
                .passwordHash(passwordEncoder.encode("currentPass"))
                .role(Role.STUDENT).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());
        token = jwtService.generateAccessToken(u.getEmail(), u.getRole().name());
    }

    @Test
    void changePasswordRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "currentPass", "newPassword", "newPass123"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void changePasswordWithValidCurrentSucceeds() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "currentPass", "newPassword", "newPass123"))))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordWithWrongCurrentReturns400() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrong", "newPassword", "newPass123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordTooShortReturns400() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "currentPass", "newPassword", "abc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserByIdRejectsUnauthorizedLookup() throws Exception {
        // Register a second user
        User other = userRepository.save(User.builder()
                .fullName("Other").email("other_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(com.rowingclub.backend.enums.Role.STUDENT)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());

        // Caller (first user, token) tries to fetch the OTHER user's profile
        mockMvc.perform(get("/api/users/" + other.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserByIdAllowsSelfLookup() throws Exception {
        User self = userRepository.findByEmail("ucuser@test.com").orElseThrow();
        mockMvc.perform(get("/api/users/" + self.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getUserByIdAllowsAdminLookup() throws Exception {
        User target = userRepository.save(User.builder()
                .fullName("Target").email("target_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(com.rowingclub.backend.enums.Role.STUDENT)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
        User admin = userRepository.save(User.builder()
                .fullName("Admin Ctrl").email("admin_get_ctrl@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(com.rowingclub.backend.enums.Role.ADMIN)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
        String adminToken = jwtService.generateAccessToken(admin.getEmail(), admin.getRole().name());

        mockMvc.perform(get("/api/users/" + target.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
