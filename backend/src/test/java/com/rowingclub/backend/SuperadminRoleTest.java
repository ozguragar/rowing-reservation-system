package com.rowingclub.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import com.rowingclub.backend.security.JwtService;
import com.rowingclub.backend.service.AuthService;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SuperadminRoleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthService authService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private User superadmin;
    private String superadminToken;

    @BeforeEach
    void setUp() {
        superadmin = userRepository.findByEmail("superadmin@system.com").orElse(null);
        if (superadmin == null) {
            superadmin = userRepository.save(User.builder()
                    .fullName("Super Admin")
                    .email("superadmin@system.com")
                    .passwordHash(passwordEncoder.encode("superadmin123"))
                    .role(Role.SUPERADMIN)
                    .isFinishedBasicTraining(true)
                    .isOnSchoolTeam(false)
                    .lessonsAttended(0)
                    .build());
        }
        superadminToken = jwtService.generateAccessToken(superadmin.getEmail(), superadmin.getRole().name());
    }

    @Test
    void superadminRoleExists() {
        assertDoesNotThrow(() -> Role.valueOf("SUPERADMIN"),
                "SUPERADMIN should be a valid Role enum value");
    }

    @Test
    void superadminCanLoginWithEmailOnly() throws Exception {
        mockMvc.perform(post("/api/superadmin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", superadmin.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void superadminCanLoginToAnyAccount() throws Exception {
        User regularUser = userRepository.save(User.builder()
                .fullName("Regular User")
                .email("regular_sa_test@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        mockMvc.perform(post("/api/superadmin/impersonate")
                        .param("email", regularUser.getEmail())
                        .header("Authorization", "Bearer " + superadminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void superadminCannotBeCreatedViaRegistration() {
        com.rowingclub.backend.dto.RegisterRequest req = new com.rowingclub.backend.dto.RegisterRequest();
        req.setFullName("Fake Superadmin");
        req.setEmail("fakesuperadmin@test.com");
        req.setPassword("pass123");
        req.setRole("SUPERADMIN");

        assertThrows(BusinessException.class, () -> authService.register(req),
                "Superadmin should only be creatable via seeding, not registration");
    }

    @Test
    void superadminCanChangePassword() {
        String newPassword = "newSuperPass456";
        assertDoesNotThrow(() -> {
            superadmin.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(superadmin);
        });

        User updated = userRepository.findByEmail("superadmin@system.com").orElseThrow();
        assertTrue(passwordEncoder.matches(newPassword, updated.getPasswordHash()));
    }

    @Test
    void nonSuperadminCannotUseEmailOnlyLogin() throws Exception {
        User regularUser = userRepository.save(User.builder()
                .fullName("Non SA User")
                .email("nonsa_login@test.com")
                .passwordHash(passwordEncoder.encode("pass123"))
                .role(Role.MEMBER)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .build());

        mockMvc.perform(post("/api/superadmin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", regularUser.getEmail()))))
                .andExpect(status().isForbidden());
    }
}
