package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder()
                .fullName("Alice Smith").email("alice@test.com")
                .passwordHash("hash").role(Role.MEMBER)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
        userRepository.save(User.builder()
                .fullName("Bob Jones").email("bob@example.org")
                .passwordHash("hash").role(Role.MEMBER)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
    }

    @Test
    void findByEmail_returnsCorrectUser() {
        assertTrue(userRepository.findByEmail("alice@test.com").isPresent());
        assertTrue(userRepository.findByEmail("nobody@x.com").isEmpty());
    }

    @Test
    void existsByEmail() {
        assertTrue(userRepository.existsByEmail("alice@test.com"));
        assertFalse(userRepository.existsByEmail("nobody@x.com"));
    }

    @Test
    void searchByNameCaseInsensitive() {
        List<User> results = userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("alice", "alice");
        assertEquals(1, results.size());
        assertEquals("Alice Smith", results.get(0).getFullName());
    }

    @Test
    void searchByEmailCaseInsensitive() {
        List<User> results = userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("EXAMPLE", "EXAMPLE");
        assertEquals(1, results.size());
        assertEquals("bob@example.org", results.get(0).getEmail());
    }

    @Test
    void searchWithNoMatchReturnsEmpty() {
        List<User> results = userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("zzznobody", "zzznobody");
        assertTrue(results.isEmpty());
    }
}
