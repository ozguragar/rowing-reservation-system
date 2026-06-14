package com.rowingclub.backend.service;

import com.rowingclub.backend.entity.*;
import com.rowingclub.backend.enums.BoatType;
import com.rowingclub.backend.enums.BookingStatus;
import com.rowingclub.backend.enums.MemberType;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.event.SchedulerCompletedEvent;
import com.rowingclub.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoSchedulerService {

    private final RowingSessionRepository sessionRepository;
    private final BoatRepository boatRepository;
    private final UserAvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Map<String, Object> runScheduler(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        List<RowingSession> sessions = sessionRepository.findByDateBetween(weekStart, weekEnd);

        List<Map<String, Object>> allAssignments = new ArrayList<>();
        int totalAssigned = 0;
        int totalBoatsUsed = 0;

        for (RowingSession session : sessions) {
            if (session.getClub() != null && !session.getClub().getFeatureAutoScheduler()) continue;

            List<Boat> eligibleBoats = boatRepository.findBySessionId(session.getId()).stream()
                    .filter(b -> b.getType() == BoatType.COASTAL && b.getCapacity() == 4)
                    .filter(b -> b.getCurrentBookings() == 0)
                    .toList();

            if (eligibleBoats.isEmpty()) continue;

            List<UserAvailability> availabilities = availabilityRepository.findBySessionIdWithUser(session.getId());

            List<User> availableStudents = availabilities.stream()
                    .map(UserAvailability::getUser)
                    .filter(u -> u.getRole() == Role.MEMBER && u.getMemberType() == MemberType.STUDENT)
                    .filter(u -> !bookingRepository.existsByUserIdAndSessionIdAndStatusNot(
                            u.getId(), session.getId(), BookingStatus.CANCELED))
                    .filter(u -> ledgerService.getBalance(u.getId()).compareTo(BigDecimal.ONE) >= 0)
                    .collect(Collectors.toList());

            List<User> availableClubMembers = availabilities.stream()
                    .map(UserAvailability::getUser)
                    .filter(u -> u.getRole() == Role.MEMBER && u.getMemberType() != MemberType.STUDENT)
                    .filter(u -> !bookingRepository.existsByUserIdAndSessionIdAndStatusNot(
                            u.getId(), session.getId(), BookingStatus.CANCELED))
                    .filter(u -> ledgerService.getBalance(u.getId()).compareTo(BigDecimal.ONE) >= 0)
                    .collect(Collectors.toList());

            Collections.shuffle(availableStudents);
            Collections.shuffle(availableClubMembers);

            int boatIndex = 0;
            List<Boat> remainingBoats = new ArrayList<>(eligibleBoats);

            // Assign club members first (separate boats)
            List<Map<String, Object>> sessionAssignments = assignGroup(
                    availableClubMembers, remainingBoats, session, boatIndex);
            allAssignments.addAll(sessionAssignments);
            totalAssigned += sessionAssignments.size();

            int usedBoats = (int) Math.ceil(sessionAssignments.size() / 4.0);
            if (usedBoats > 0) {
                remainingBoats = remainingBoats.subList(
                        Math.min(usedBoats, remainingBoats.size()), remainingBoats.size());
            }

            // Assign students (separate boats)
            List<Map<String, Object>> studentAssignments = assignGroup(
                    availableStudents, remainingBoats, session, boatIndex + usedBoats);
            allAssignments.addAll(studentAssignments);
            totalAssigned += studentAssignments.size();
            totalBoatsUsed += usedBoats + (int) Math.ceil(studentAssignments.size() / 4.0);
        }

        eventPublisher.publishEvent(new SchedulerCompletedEvent(this, allAssignments));

        Map<String, Object> result = new HashMap<>();
        result.put("totalAssigned", totalAssigned);
        result.put("totalBoatsUsed", totalBoatsUsed);
        result.put("weekStart", weekStart.toString());
        result.put("weekEnd", weekEnd.toString());
        result.put("assignments", allAssignments);
        return result;
    }

    private List<Map<String, Object>> assignGroup(
            List<User> users, List<Boat> boats, RowingSession session, int startBoatIdx) {

        List<Map<String, Object>> assignments = new ArrayList<>();
        if (users.isEmpty() || boats.isEmpty()) return assignments;

        int userIdx = 0;
        for (Boat boat : boats) {
            if (userIdx >= users.size()) break;

            int remaining = users.size() - userIdx;
            // Need at least 3 people for a boat, or exactly 4
            if (remaining < 3) break;

            int crewSize = Math.min(4, remaining);
            // If we'd leave 1 or 2 people for the next boat, adjust
            int afterThis = remaining - crewSize;
            if (afterThis == 1 || afterThis == 2) {
                if (remaining >= 7) {
                    crewSize = 4; // take 4, leave 3+ for next
                } else if (remaining == 4) {
                    crewSize = 4;
                } else if (remaining == 3) {
                    crewSize = 3;
                } else {
                    crewSize = 3; // take 3, leave remainder for next boat
                }
            }

            for (int i = 0; i < crewSize && userIdx < users.size(); i++) {
                User user = users.get(userIdx);
                Booking booking = Booking.builder()
                        .user(user)
                        .boat(boat)
                        .session(session)
                        .status(BookingStatus.AUTO_ASSIGNED)
                        .build();
                bookingRepository.save(booking);

                boat.setCurrentBookings(boat.getCurrentBookings() + 1);
                boatRepository.save(boat);

                ledgerService.deductCredit(user.getId(), BigDecimal.ONE,
                        "Auto-scheduled: " + session.getDate() + " " + session.getStartTime());

                user.setLessonsAttended(user.getLessonsAttended() + 1);
                userRepository.save(user);

                Map<String, Object> assignment = new HashMap<>();
                assignment.put("userId", user.getId());
                assignment.put("userName", user.getFullName());
                assignment.put("boatName", boat.getName());
                assignment.put("sessionInfo", session.getDate() + " " + session.getStartTime());
                assignments.add(assignment);

                userIdx++;
            }
        }
        return assignments;
    }
}
