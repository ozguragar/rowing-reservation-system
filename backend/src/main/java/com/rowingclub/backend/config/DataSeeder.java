package com.rowingclub.backend.config;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.*;
import com.rowingclub.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("!prod")   // Seed only outside the prod profile
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RowingSessionRepository sessionRepository;
    private final BoatRepository boatRepository;
    private final FinancialLedgerRepository ledgerRepository;
    private final AppSettingRepository appSettingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded, skipping...");
            return;
        }

        log.info("Seeding database...");
        seedSettings();
        List<User> allUsers = new ArrayList<>();
        allUsers.addAll(seedAdmins());
        allUsers.addAll(seedStudents());
        allUsers.addAll(seedClubMembers());
        seedSessions();
        seedCredits(allUsers);
        log.info("Database seeding completed!");
    }

    private void seedSettings() {
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_next_day_only").settingValue("false").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("show_booked_members").settingValue("true").build());
        appSettingRepository.save(AppSetting.builder()
                .settingKey("student_booking_hour").settingValue("16").build());
    }

    private List<User> seedAdmins() {
        List<User> admins = new ArrayList<>();
        admins.add(createUser("Admin One", "admin1@rowingclub.com", "admin123", Role.ADMIN, true, false));
        admins.add(createUser("Admin Two", "admin2@rowingclub.com", "admin123", Role.ADMIN, true, false));
        log.info("Created 2 admins");
        return admins;
    }

    private List<User> seedStudents() {
        List<User> students = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            boolean basicTraining = i <= 45; // 90% finished basic training
            boolean onTeam = i <= 10;
            students.add(createUser(
                    "Student " + String.format("%02d", i),
                    "student" + i + "@university.edu",
                    "student123",
                    Role.STUDENT,
                    basicTraining,
                    onTeam
            ));
        }
        log.info("Created 50 students (45 trained, 5 untrained)");
        return students;
    }

    private List<User> seedClubMembers() {
        List<User> members = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            boolean basicTraining = i <= 36; // 90% finished basic training
            members.add(createUser(
                    "Member " + String.format("%02d", i),
                    "member" + i + "@rowingclub.com",
                    "member123",
                    Role.CLUB_MEMBER,
                    basicTraining,
                    false
            ));
        }
        log.info("Created 40 club members (36 trained, 4 untrained)");
        return members;
    }

    private User createUser(String name, String email, String password, Role role,
                           boolean basicTraining, boolean onTeam) {
        return userRepository.save(User.builder()
                .fullName(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .isFinishedBasicTraining(basicTraining)
                .isOnSchoolTeam(onTeam)
                .lessonsAttended((int) (Math.random() * 30))
                .build());
    }

    private void seedSessions() {
        LocalDate today = LocalDate.now();

        for (int dayOffset = 0; dayOffset < 14; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);
            if (date.getDayOfWeek() == DayOfWeek.MONDAY) continue;

            // Morning sessions: 6:20, 7:20, 8:20, 9:20
            LocalTime[] morningStarts = {
                LocalTime.of(6, 20), LocalTime.of(7, 20),
                LocalTime.of(8, 20), LocalTime.of(9, 20)
            };
            for (LocalTime start : morningStarts) {
                RowingSession session = createSession(date, start, start.plusHours(1));
                addDefaultBoats(session);
            }

            // Afternoon sessions: 16:20, 17:20, 18:20, 19:20, 20:20
            LocalTime[] afternoonStarts = {
                LocalTime.of(16, 20), LocalTime.of(17, 20),
                LocalTime.of(18, 20), LocalTime.of(19, 20),
                LocalTime.of(20, 20)
            };
            for (LocalTime start : afternoonStarts) {
                RowingSession session = createSession(date, start, start.plusHours(1));
                addDefaultBoats(session);
            }
        }
        log.info("Created sessions for the next 14 days (excluding Mondays)");
    }

    private RowingSession createSession(LocalDate date, LocalTime start, LocalTime end) {
        return sessionRepository.save(RowingSession.builder()
                .date(date)
                .startTime(start)
                .endTime(end)
                .status(SessionStatus.APPROVED)
                .build());
    }

    private void addDefaultBoats(RowingSession session) {
        // Two 4-person coastal boats
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0)
                .name("Coastal 4x A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0)
                .name("Coastal 4x B").build());

        // Two 2-person coastal boats
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(false).currentBookings(0)
                .name("Coastal 2x A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(false).currentBookings(0)
                .name("Coastal 2x B").build());

        // Two 1-person coastal boats
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(1)
                .isBasicTrainingBoat(false).currentBookings(0)
                .name("Coastal 1x A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(1)
                .isBasicTrainingBoat(false).currentBookings(0)
                .name("Coastal 1x B").build());
    }

    private void seedCredits(List<User> users) {
        for (User user : users) {
            if (user.getRole() == Role.ADMIN) continue;

            BigDecimal credits = BigDecimal.valueOf(5 + (int) (Math.random() * 16));
            ledgerRepository.save(FinancialLedger.builder()
                    .user(user)
                    .amount(credits)
                    .reason("Initial credit allocation")
                    .runningBalance(credits)
                    .timestamp(LocalDateTime.now())
                    .expirationDate(LocalDateTime.now().plusMonths(3))
                    .build());
        }
        log.info("Seeded credits for all users");
    }
}
