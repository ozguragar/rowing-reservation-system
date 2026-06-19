package com.rowingclub.backend;

import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.entity.FinancialLedger;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.MemberType;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.ClubRepository;
import com.rowingclub.backend.repository.FinancialLedgerRepository;
import com.rowingclub.backend.repository.UserRepository;
import com.rowingclub.backend.service.UserService;
import org.springframework.security.access.AccessDeniedException;
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

    // --- updateRoleAndType guardrails ---

    private User saveUser(String email, Role role, Club c) {
        return userRepository.save(User.builder()
                .club(c).fullName(email).email(email)
                .passwordHash(passwordEncoder.encode("pass"))
                .role(role).memberType(MemberType.STUDENT)
                .isFinishedBasicTraining(true).isOnSchoolTeam(false).lessonsAttended(0).build());
    }

    @Test
    void updateRoleAndTypeChangesBoth() {
        User admin = saveUser("urt_admin@test.com", Role.CLUB_ADMIN, club);
        User target = saveUser("urt_target@test.com", Role.MEMBER, club);

        var dto = userService.updateRoleAndType(target.getId(), "TRAINER", "DEFAULT", admin.getEmail());

        assertEquals("TRAINER", dto.getRole());
        assertEquals("DEFAULT", dto.getMemberType());
    }

    @Test
    void cannotEditOwnRole() {
        User admin = saveUser("urt_self@test.com", Role.CLUB_ADMIN, club);
        assertThrows(BusinessException.class,
                () -> userService.updateRoleAndType(admin.getId(), "MEMBER", null, admin.getEmail()));
    }

    @Test
    void clubAdminCannotGrantSuperadmin() {
        User admin = saveUser("urt_ca@test.com", Role.CLUB_ADMIN, club);
        User target = saveUser("urt_victim@test.com", Role.MEMBER, club);
        assertThrows(BusinessException.class,
                () -> userService.updateRoleAndType(target.getId(), "SUPERADMIN", null, admin.getEmail()));
    }

    @Test
    void cannotEditUserInAnotherClub() {
        Club otherClub = clubRepository.save(Club.builder()
                .name("Other UST Club")
                .featureAvailabilityModule(true).featureCancellationRequests(true)
                .featureAutoScheduler(true).featureShowBookedMembers(true).build());
        User admin = saveUser("urt_xadmin@test.com", Role.CLUB_ADMIN, club);
        User target = saveUser("urt_xtarget@test.com", Role.MEMBER, otherClub);

        assertThrows(AccessDeniedException.class,
                () -> userService.updateRoleAndType(target.getId(), "TRAINER", null, admin.getEmail()));
    }

    @Test
    void invalidRoleThrows() {
        User admin = saveUser("urt_badrole_admin@test.com", Role.CLUB_ADMIN, club);
        User target = saveUser("urt_badrole_target@test.com", Role.MEMBER, club);
        assertThrows(BusinessException.class,
                () -> userService.updateRoleAndType(target.getId(), "WIZARD", null, admin.getEmail()));
    }

    @Test
    void superadminCanGrantSuperadmin() {
        User superadmin = userRepository.save(User.builder()
                .fullName("Plat Super").email("urt_super@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .role(Role.SUPERADMIN).isFinishedBasicTraining(true)
                .isOnSchoolTeam(false).lessonsAttended(0).build());
        User target = saveUser("urt_promote@test.com", Role.MEMBER, club);

        var dto = userService.updateRoleAndType(target.getId(), "SUPERADMIN", null, superadmin.getEmail());
        assertEquals("SUPERADMIN", dto.getRole());
    }

    @Test
    void updateMemberTypeOnlyLeavesRoleUnchanged() {
        User admin = saveUser("urt_mtadmin@test.com", Role.CLUB_ADMIN, club);
        User target = saveUser("urt_mttarget@test.com", Role.MEMBER, club);

        var dto = userService.updateRoleAndType(target.getId(), null, "RECREATIONAL", admin.getEmail());
        assertEquals("MEMBER", dto.getRole());
        assertEquals("RECREATIONAL", dto.getMemberType());
    }

    @Test
    void invalidMemberTypeThrows() {
        User admin = saveUser("urt_badmt_admin@test.com", Role.CLUB_ADMIN, club);
        User target = saveUser("urt_badmt_target@test.com", Role.MEMBER, club);
        assertThrows(BusinessException.class,
                () -> userService.updateRoleAndType(target.getId(), null, "GHOST", admin.getEmail()));
    }

    @Test
    void getUserByIdNotFoundThrows() {
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(999_999L));
    }

    @Test
    void incrementLessonsAttendedAddsOne() {
        User u = saveUser("urt_lessons@test.com", Role.MEMBER, club);
        int before = u.getLessonsAttended();
        userService.incrementLessonsAttended(u.getId());
        assertEquals(before + 1, userRepository.findById(u.getId()).orElseThrow().getLessonsAttended());
    }

    @Test
    void searchAndGetAllAreScopedToClub() {
        Club otherClub = clubRepository.save(Club.builder()
                .name("Other UST Search Club")
                .featureAvailabilityModule(true).featureCancellationRequests(true)
                .featureAutoScheduler(true).featureShowBookedMembers(true).build());
        User mine = saveUser("uniquesearchname@test.com", Role.MEMBER, club);
        saveUser("uniquesearchname_other@test.com", Role.MEMBER, otherClub);

        var found = userService.searchUsers(club.getId(), "uniquesearchname");
        assertTrue(found.stream().anyMatch(u -> u.getId().equals(mine.getId())));
        assertTrue(found.stream().allMatch(u -> club.getId().equals(u.getClubId())));

        var all = userService.getAllUsers(club.getId());
        assertTrue(all.stream().allMatch(u -> club.getId().equals(u.getClubId())));
    }
}
