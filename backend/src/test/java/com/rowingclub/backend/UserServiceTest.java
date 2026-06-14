package com.rowingclub.backend;

import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.entity.FinancialLedger;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.repository.ClubRepository;
import com.rowingclub.backend.repository.FinancialLedgerRepository;
import com.rowingclub.backend.repository.UserRepository;
import com.rowingclub.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private FinancialLedgerRepository ledgerRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private Club club;

    @BeforeEach
    void setUp() {
        club = clubRepository.save(Club.builder()
                .name("UserServiceTest Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build());
        user = userRepository.save(User.builder()
                .fullName("Pass User").email("passuser@test.com")
                .passwordHash(passwordEncoder.encode("oldPass123"))
                .role(Role.MEMBER).isFinishedBasicTraining(false)
                .isOnSchoolTeam(false).lessonsAttended(3).build());
    }

    @Test
    void changePasswordSuccess() {
        userService.changePassword("passuser@test.com", "oldPass123", "newPass456");
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("newPass456", refreshed.getPasswordHash()));
    }

    @Test
    void changePasswordWithWrongCurrentThrows() {
        assertThrows(BusinessException.class,
                () -> userService.changePassword("passuser@test.com", "wrongOld", "newPass456"));
    }

    @Test
    void setBasicTrainingFlipsFlag() {
        var updated = userService.setBasicTrainingFinished(user.getId(), true);
        assertTrue(updated.getIsFinishedBasicTraining());
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(refreshed.getIsFinishedBasicTraining());
    }

    @Test
    void getUserByEmailPopulatesEarliestExpiration() {
        LocalDateTime future = LocalDateTime.now().plusDays(30);
        ledgerRepository.save(FinancialLedger.builder()
                .club(club).user(user).amount(BigDecimal.TEN).reason("Credits")
                .runningBalance(BigDecimal.TEN).timestamp(LocalDateTime.now())
                .expirationDate(future).build());

        var dto = userService.getUserByEmail("passuser@test.com");
        assertNotNull(dto.getEarliestCreditExpiration());
    }

    @Test
    void getUserByEmailEarliestExpirationNullWhenNoneSet() {
        var dto = userService.getUserByEmail("passuser@test.com");
        assertNull(dto.getEarliestCreditExpiration());
    }
}
