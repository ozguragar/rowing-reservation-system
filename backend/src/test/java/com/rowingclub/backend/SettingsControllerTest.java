package com.rowingclub.backend;

import com.rowingclub.backend.entity.AppSetting;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.repository.AppSettingRepository;
import com.rowingclub.backend.repository.UserRepository;
import com.rowingclub.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppSettingRepository appSettingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userToken;

    @BeforeEach
    void setUp() {
        User member = userRepository.save(User.builder()
                .fullName("Settings User").email("settings_user@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.STUDENT).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());
        userToken = jwtService.generateAccessToken(member.getEmail(), member.getRole().name());
    }

    @Test
    void publicSettingsRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/settings/public"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void publicSettingsReturns200WithAuth() throws Exception {
        mockMvc.perform(get("/api/settings/public")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void publicSettingsReturnsStoredKeys() throws Exception {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_next_day_only").settingValue("false").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("16").build());

        mockMvc.perform(get("/api/settings/public")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student_next_day_only").value("false"))
                .andExpect(jsonPath("$.student_booking_hour").value("16"));
    }

    @Test
    void publicSettingsIncludesNewBookingHourDisabledAndAvailabilityKeys() throws Exception {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("booking_hour_disabled").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("disable_availability").settingValue("true").build());

        mockMvc.perform(get("/api/settings/public")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking_hour_disabled").value("true"))
                .andExpect(jsonPath("$.disable_availability").value("true"));
    }
}
