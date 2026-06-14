package com.rowingclub.backend.service;

import com.rowingclub.backend.dto.SessionDto;
import com.rowingclub.backend.entity.RowingSession;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.entity.UserAvailability;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.RowingSessionRepository;
import com.rowingclub.backend.repository.UserAvailabilityRepository;
import com.rowingclub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final UserAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final RowingSessionRepository sessionRepository;

    @Transactional
    public void setAvailability(String userEmail, Long sessionId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        RowingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!availabilityRepository.existsByUserIdAndSessionId(user.getId(), sessionId)) {
            UserAvailability availability = UserAvailability.builder()
                    .user(user)
                    .session(session)
                    .build();
            availabilityRepository.save(availability);
        }
    }

    @Transactional
    public void removeAvailability(String userEmail, Long sessionId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        availabilityRepository.deleteByUserIdAndSessionId(user.getId(), sessionId);
    }

    public List<Long> getUserAvailableSessions(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return availabilityRepository.findByUserId(user.getId())
                .stream().map(a -> a.getSession().getId()).toList();
    }

    public List<SessionDto> getWeekSessions(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return sessionRepository.findByDateBetween(weekStart, weekEnd)
                .stream().map(SessionDto::from).toList();
    }
}
