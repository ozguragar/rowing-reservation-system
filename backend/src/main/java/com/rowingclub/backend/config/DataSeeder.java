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
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RowingSessionRepository sessionRepository;
    private final BoatRepository boatRepository;
    private final FinancialLedgerRepository ledgerRepository;
    private final AppSettingRepository appSettingRepository;
    private final ClubRepository clubRepository;
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
        seedSuperadmin();
        List<Club> clubs = seedClubs();
        
        for (Club club : clubs) {
            List<User> clubUsers = new ArrayList<>();
            clubUsers.addAll(seedClubAdmins(club));
            clubUsers.addAll(seedTrainers(club));
            clubUsers.addAll(seedMembers(club));
            seedSessions(club);
            seedCredits(clubUsers, club);
        }
        
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

    private void seedSuperadmin() {
        User superadmin = userRepository.save(User.builder()
                .fullName("Super Admin")
                .email("superadmin@rowingclub.com")
                .passwordHash(passwordEncoder.encode("superadmin123"))
                .role(Role.SUPERADMIN)
                .isFinishedBasicTraining(true)
                .isOnSchoolTeam(false)
                .lessonsAttended(0)
                .isCox(false)
                .build());
        log.info("Created superadmin: {}", superadmin.getEmail());
    }

    private List<Club> seedClubs() {
        List<Club> clubs = new ArrayList<>();
        clubs.add(clubRepository.save(Club.builder()
                .name("Riverside Rowing Club")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build()));
        clubs.add(clubRepository.save(Club.builder()
                .name("University Rowing Team")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build()));
        clubs.add(clubRepository.save(Club.builder()
                .name("Metropolitan Rowing Association")
                .featureAvailabilityModule(true)
                .featureCancellationRequests(true)
                .featureAutoScheduler(true)
                .featureShowBookedMembers(true)
                .build()));
        log.info("Created 3 clubs");
        return clubs;
    }

    private List<User> seedClubAdmins(Club club) {
        List<User> admins = new ArrayList<>();
        admins.add(createUser(
                club, 
                "Admin " + club.getName(), 
                "admin@" + club.getName().toLowerCase().replace(" ", "") + ".com",
                "admin123", 
                Role.CLUB_ADMIN, 
                MemberType.DEFAULT,
                true, 
                false, 
                false
        ));
        log.info("Created 1 club admin for {}", club.getName());
        return admins;
    }

    private List<User> seedTrainers(Club club) {
        List<User> trainers = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            trainers.add(createUser(
                    club,
                    "Trainer " + i + " " + club.getName(),
                    "trainer" + i + "@" + club.getName().toLowerCase().replace(" ", "") + ".com",
                    "trainer123",
                    Role.TRAINER,
                    MemberType.DEFAULT,
                    true,
                    false,
                    true
            ));
        }
        log.info("Created 2 trainers for {}", club.getName());
        return trainers;
    }

    private List<User> seedMembers(Club club) {
        List<User> members = new ArrayList<>();
        MemberType[] types = {MemberType.STUDENT, MemberType.RECREATIONAL, MemberType.DEFAULT};
        
        for (int i = 1; i <= 30; i++) {
            boolean basicTraining = i <= 27;
            boolean onTeam = i <= 5;
            boolean isCox = i <= 3;
            MemberType memberType = types[i % 3];
            
            members.add(createUser(
                    club,
                    "Member " + String.format("%02d", i) + " " + club.getName(),
                    "member" + i + "@" + club.getName().toLowerCase().replace(" ", "") + ".com",
                    "member123",
                    Role.MEMBER,
                    memberType,
                    basicTraining,
                    onTeam,
                    isCox
            ));
        }
        log.info("Created 30 members for {} (27 trained, 3 untrained, mixed types)", club.getName());
        return members;
    }

    private User createUser(Club club, String name, String email, String password, Role role,
                           MemberType memberType, boolean basicTraining, boolean onTeam, boolean isCox) {
        return userRepository.save(User.builder()
                .club(club)
                .fullName(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .memberType(memberType)
                .isFinishedBasicTraining(basicTraining)
                .isOnSchoolTeam(onTeam)
                .lessonsAttended((int) (Math.random() * 30))
                .isCox(isCox)
                .build());
    }

    private void seedSessions(Club club) {
        LocalDate today = LocalDate.now();

        for (int dayOffset = 0; dayOffset < 14; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);
            if (date.getDayOfWeek() == DayOfWeek.MONDAY) continue;

            LocalTime[] morningStarts = {
                LocalTime.of(6, 20), LocalTime.of(7, 20),
                LocalTime.of(8, 20), LocalTime.of(9, 20)
            };
            for (LocalTime start : morningStarts) {
                RowingSession session = createSession(club, date, start, start.plusHours(1));
                addDefaultBoats(session);
            }

            LocalTime[] afternoonStarts = {
                LocalTime.of(16, 20), LocalTime.of(17, 20),
                LocalTime.of(18, 20), LocalTime.of(19, 20),
                LocalTime.of(20, 20)
            };
            for (LocalTime start : afternoonStarts) {
                RowingSession session = createSession(club, date, start, start.plusHours(1));
                addDefaultBoats(session);
            }
        }
        log.info("Created sessions for {} for the next 14 days (excluding Mondays)", club.getName());
    }

    private RowingSession createSession(Club club, LocalDate date, LocalTime start, LocalTime end) {
        return sessionRepository.save(RowingSession.builder()
                .club(club)
                .date(date)
                .startTime(start)
                .endTime(end)
                .status(SessionStatus.APPROVED)
                .build());
    }

    private void addDefaultBoats(RowingSession session) {
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(true).currentBookings(0)
                .hasCoxSeat(true)
                .name("Coastal 4x+ A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(4)
                .isBasicTrainingBoat(false).currentBookings(0)
                .hasCoxSeat(false)
                .name("Coastal 4x B").build());

        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(false).currentBookings(0)
                .hasCoxSeat(false)
                .name("Coastal 2x A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(2)
                .isBasicTrainingBoat(false).currentBookings(0)
                .hasCoxSeat(false)
                .name("Coastal 2x B").build());

        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(1)
                .isBasicTrainingBoat(false).currentBookings(0)
                .hasCoxSeat(false)
                .name("Coastal 1x A").build());
        boatRepository.save(Boat.builder()
                .session(session).type(BoatType.COASTAL).capacity(1)
                .isBasicTrainingBoat(false).currentBookings(0)
                .hasCoxSeat(false)
                .name("Coastal 1x B").build());
    }

    private void seedCredits(List<User> users, Club club) {
        for (User user : users) {
            if (user.getRole() == Role.CLUB_ADMIN || user.getRole() == Role.TRAINER) continue;

            BigDecimal credits = BigDecimal.valueOf(5 + (int) (Math.random() * 16));
            ledgerRepository.save(FinancialLedger.builder()
                    .user(user)
                    .club(club)
                    .amount(credits)
                    .reason("Initial credit allocation")
                    .runningBalance(credits)
                    .timestamp(LocalDateTime.now())
                    .expirationDate(LocalDateTime.now().plusMonths(3))
                    .build());
        }
        log.info("Seeded credits for members in {}", club.getName());
    }
}
