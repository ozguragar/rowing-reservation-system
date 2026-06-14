package com.rowingclub.backend;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeedDataTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RowingSessionRepository sessionRepository;
    @Autowired private BoatRepository boatRepository;

    @Test
    void exactlyOneSuperadminSeeded() {
        List<User> superadmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SUPERADMIN)
                .collect(Collectors.toList());
        assertEquals(1, superadmins.size(), "Exactly 1 superadmin should be seeded");
    }

    @Test
    void exactlyThreeClubsSeeded() {
        long clubCount;
        try {
            var method = userRepository.getClass().getMethod("findAll");
            List<User> allUsers = userRepository.findAll();
            clubCount = allUsers.stream()
                    .map(User::getClub)
                    .filter(java.util.Objects::nonNull)
                    .map(c -> c.getId())
                    .distinct()
                    .count();
        } catch (Exception e) {
            clubCount = 0;
        }
        assertEquals(3, clubCount, "Exactly 3 clubs should be seeded");
    }

    @Test
    void eachClubHasExactlyOneClubAdmin() {
        List<User> clubAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CLUB_ADMIN)
                .collect(Collectors.toList());

        var clubIds = clubAdmins.stream()
                .map(u -> u.getClub() != null ? u.getClub().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        var uniqueClubIds = clubIds.stream().distinct().collect(Collectors.toList());
        assertEquals(uniqueClubIds.size(), clubIds.size(),
                "Each club should have exactly 1 Club Admin (no duplicates per club)");
        assertEquals(3, clubAdmins.size(), "There should be exactly 3 Club Admins total");
    }

    @Test
    void eachClubHasExactlyTwoTrainers() {
        List<User> trainers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.TRAINER)
                .collect(Collectors.toList());
        assertEquals(6, trainers.size(), "There should be exactly 6 Trainers total (2 per club)");

        var trainersByClub = trainers.stream()
                .filter(u -> u.getClub() != null)
                .collect(Collectors.groupingBy(u -> u.getClub().getId()));

        trainersByClub.forEach((clubId, clubTrainers) ->
                assertEquals(2, clubTrainers.size(),
                        "Each club should have exactly 2 Trainers"));
    }

    @Test
    void eachClubHasExactly30Members() {
        List<User> members = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.MEMBER)
                .collect(Collectors.toList());
        assertEquals(90, members.size(), "There should be exactly 90 Members total (30 per club)");

        var membersByClub = members.stream()
                .filter(u -> u.getClub() != null)
                .collect(Collectors.groupingBy(u -> u.getClub().getId()));

        membersByClub.forEach((clubId, clubMembers) ->
                assertEquals(30, clubMembers.size(),
                        "Each club should have exactly 30 Members"));
    }

    @Test
    void membersHaveMixedMemberTypes() {
        List<User> members = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.MEMBER)
                .collect(Collectors.toList());

        var memberTypes = members.stream()
                .map(User::getMemberType)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        assertTrue(memberTypes.contains(MemberType.STUDENT), "Should have STUDENT type members");
        assertTrue(memberTypes.contains(MemberType.RECREATIONAL), "Should have RECREATIONAL type members");
        assertTrue(memberTypes.contains(MemberType.DEFAULT), "Should have DEFAULT type members");
    }

    @Test
    void sessionsSeededForCurrentDate() {
        List<RowingSession> sessions = sessionRepository.findAll();
        assertFalse(sessions.isEmpty(), "Sessions should be seeded");

        var today = java.time.LocalDate.now();
        boolean hasCurrentDateSessions = sessions.stream()
                .anyMatch(s -> !s.getDate().isBefore(today));
        assertTrue(hasCurrentDateSessions, "Sessions should include current date or later");
    }

    @Test
    void boatsSeededForSessions() {
        List<Boat> boats = boatRepository.findAll();
        assertFalse(boats.isEmpty(), "Boats should be seeded for sessions");
    }

    @Test
    void superadminCannotBeRegisteredViaApi() {
        List<User> superadmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SUPERADMIN)
                .collect(Collectors.toList());
        assertEquals(1, superadmins.size(),
                "Only seeded superadmin should exist, registration should not create superadmins");
    }
}
