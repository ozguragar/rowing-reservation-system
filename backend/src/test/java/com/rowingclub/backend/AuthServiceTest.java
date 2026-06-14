package com.rowingclub.backend;

import com.rowingclub.backend.dto.AuthRequest;
import com.rowingclub.backend.dto.AuthResponse;
import com.rowingclub.backend.dto.RegisterRequest;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Test
    void registerNewUser() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("newuser@test.com");
        req.setPassword("password123");
        req.setRole("MEMBER");

        AuthResponse response = authService.register(req);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("newuser@test.com", response.getUser().getEmail());
        assertEquals("MEMBER", response.getUser().getRole());
    }

    @Test
    void registerDuplicateEmailFails() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("duplicate@test.com");
        req.setPassword("password123");
        authService.register(req);

        RegisterRequest req2 = new RegisterRequest();
        req2.setFullName("Test User 2");
        req2.setEmail("duplicate@test.com");
        req2.setPassword("password456");

        assertThrows(BusinessException.class, () -> authService.register(req2));
    }

    @Test
    void loginSuccess() {
        RegisterRequest reg = new RegisterRequest();
        reg.setFullName("Login User");
        reg.setEmail("login@test.com");
        reg.setPassword("mypassword");
        authService.register(reg);

        AuthRequest login = new AuthRequest();
        login.setEmail("login@test.com");
        login.setPassword("mypassword");
        AuthResponse response = authService.login(login);

        assertNotNull(response.getAccessToken());
        assertEquals("login@test.com", response.getUser().getEmail());
    }

    @Test
    void loginWrongPasswordFails() {
        RegisterRequest reg = new RegisterRequest();
        reg.setFullName("Wrong Pass");
        reg.setEmail("wrong@test.com");
        reg.setPassword("correctpass");
        authService.register(reg);

        AuthRequest login = new AuthRequest();
        login.setEmail("wrong@test.com");
        login.setPassword("incorrectpass");

        assertThrows(BusinessException.class, () -> authService.login(login));
    }

    @Test
    void refreshTokenWorks() {
        RegisterRequest reg = new RegisterRequest();
        reg.setFullName("Refresh User");
        reg.setEmail("refresh@test.com");
        reg.setPassword("password123");
        AuthResponse initial = authService.register(reg);

        AuthResponse refreshed = authService.refresh(initial.getRefreshToken());

        assertNotNull(refreshed.getAccessToken());
        assertNotNull(refreshed.getRefreshToken());
        assertEquals("refresh@test.com", refreshed.getUser().getEmail());
    }

    @Test
    void refreshWithInvalidTokenFails() {
        assertThrows(BusinessException.class, () -> authService.refresh("not.a.valid.token"));
    }

    @Test
    void loginUnknownEmailFails() {
        AuthRequest login = new AuthRequest();
        login.setEmail("nobody@test.com");
        login.setPassword("pass");
        assertThrows(BusinessException.class, () -> authService.login(login));
    }

    @Test
    void registerWithNullRoleDefaultsToStudent() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Default Role");
        req.setEmail("defaultrole@test.com");
        req.setPassword("pass123");
        // role intentionally null
        AuthResponse response = authService.register(req);
        assertEquals("MEMBER", response.getUser().getRole());
    }
}
