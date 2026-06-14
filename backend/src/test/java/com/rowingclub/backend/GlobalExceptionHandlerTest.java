package com.rowingclub.backend;

import com.rowingclub.backend.entity.*;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.builder()
                .fullName("Ex Admin").email("ex_admin@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.ADMIN).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());
        adminToken = jwtService.generateAccessToken(admin.getEmail(), admin.getRole().name());
    }

    @Test
    void businessExceptionReturns400WithMessage() throws Exception {
        // Attempting login with wrong password triggers BusinessException
        // First register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "X", "email", "ex_user@test.com", "password", "correct1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ex_user@test.com", "password", "wrong"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void resourceNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/sessions/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorReturns400() throws Exception {
        // Register with empty body (missing required fields)
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
