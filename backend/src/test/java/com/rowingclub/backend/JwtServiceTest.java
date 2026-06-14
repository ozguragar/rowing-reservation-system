package com.rowingclub.backend;

import com.rowingclub.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void accessTokenContainsEmailAndRole() {
        String token = jwtService.generateAccessToken("user@test.com", "MEMBER");
        assertEquals("user@test.com", jwtService.extractEmail(token));
        assertEquals("MEMBER", jwtService.extractRole(token));
    }

    @Test
    void refreshTokenHasNoRole() {
        String token = jwtService.generateRefreshToken("user@test.com");
        assertEquals("user@test.com", jwtService.extractEmail(token));
        assertNull(jwtService.extractRole(token));
    }

    @Test
    void validTokenIsValid() {
        String token = jwtService.generateAccessToken("user@test.com", "CLUB_ADMIN");
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtService.generateAccessToken("user@test.com", "MEMBER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtService.isTokenValid(tampered));
    }

    @Test
    void randomStringIsInvalid() {
        assertFalse(jwtService.isTokenValid("not.a.jwt.at.all"));
    }

    @Test
    void differentUsersGetDifferentTokens() {
        String t1 = jwtService.generateAccessToken("a@test.com", "MEMBER");
        String t2 = jwtService.generateAccessToken("b@test.com", "MEMBER");
        assertNotEquals(t1, t2);
    }
}
