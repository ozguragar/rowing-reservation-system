package com.rowingclub.backend;

import com.rowingclub.backend.dto.AuthRequest;
import com.rowingclub.backend.dto.AuthResponse;
import com.rowingclub.backend.dto.RegisterRequest;
import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.ClubRepository;
import com.rowingclub.backend.repository.UserRepository;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClubRepository clubRepository;

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
    void registerAlwaysCreatesMember() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Default Role");
        req.setEmail("defaultrole@test.com");
        req.setPassword("pass1234");
        // role intentionally null
        AuthResponse response = authService.register(req);
        assertEquals("MEMBER", response.getUser().getRole());
    }

    @Test
    void registerAssignsDefaultClub() {
        // No clubId given — should fall back to the primary (lowest-id) club so that
        // the NOT NULL club_id constraint is satisfied.
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Club Default");
        req.setEmail("club_default@test.com");
        req.setPassword("password1");
        authService.register(req);

        User saved = userRepository.findByEmail("club_default@test.com").orElseThrow();
        assertNotNull(saved.getClub(), "new member must belong to a club");
    }

    @Test
    void registerHonoursExplicitClubId() {
        Club club = clubRepository.save(Club.builder()
                .name("Explicit Club " + System.nanoTime())
                .featureAvailabilityModule(true).featureCancellationRequests(true)
                .featureAutoScheduler(true).featureShowBookedMembers(true).build());

        RegisterRequest req = new RegisterRequest();
        req.setFullName("Explicit Club Member");
        req.setEmail("explicit_club@test.com");
        req.setPassword("password1");
        req.setClubId(club.getId());
        authService.register(req);

        User saved = userRepository.findByEmail("explicit_club@test.com").orElseThrow();
        assertEquals(club.getId(), saved.getClub().getId());
    }

    @Test
    void registerWithUnknownClubIdFails() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Bad Club");
        req.setEmail("bad_club@test.com");
        req.setPassword("password1");
        req.setClubId(999_999L);
        assertThrows(BusinessException.class, () -> authService.register(req));
    }

    @Test
    void registerNeverGrantsElevatedRole() {
        // A self-registering user must not be able to become a TRAINER/admin.
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Sneaky Trainer");
        req.setEmail("sneaky@test.com");
        req.setPassword("password1");
        req.setRole("TRAINER");
        AuthResponse response = authService.register(req);
        assertEquals("MEMBER", response.getUser().getRole());
    }
}
