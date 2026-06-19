package com.rowingclub.backend.service;

import com.rowingclub.backend.dto.*;
import com.rowingclub.backend.entity.Boat;
import com.rowingclub.backend.entity.Club;
import com.rowingclub.backend.entity.RowingSession;
import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.BoatType;
import com.rowingclub.backend.enums.Role;
import com.rowingclub.backend.enums.SessionStatus;
import com.rowingclub.backend.exception.BusinessException;
import com.rowingclub.backend.exception.ResourceNotFoundException;
import com.rowingclub.backend.repository.BoatRepository;
import com.rowingclub.backend.repository.BookingRepository;
import com.rowingclub.backend.repository.RowingSessionRepository;
import com.rowingclub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rowingclub.backend.enums.BookingStatus;
import com.rowingclub.backend.entity.Booking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final RowingSessionRepository sessionRepository;
    private final BoatRepository boatRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public Long getClubIdForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() == Role.SUPERADMIN) return null;
        return user.getClub() != null ? user.getClub().getId() : null;
    }

    public Club getClubForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getClub();
    }

    @Transactional
    public SessionDto createSession(CreateSessionRequest request) {
        return createSession(request, null);
    }

    @Transactional
    public SessionDto createSession(CreateSessionRequest request, Club club) {
        RowingSession session = RowingSession.builder()
                .club(club)
                .date(request.getDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SessionStatus.DRAFT)
                .build();
        return SessionDto.from(sessionRepository.save(session));
    }

    @Transactional
    public List<SessionDto> createBulkSessions(List<CreateSessionRequest> requests, Club club) {
        return requests.stream().map(r -> createSession(r, club)).toList();
    }

    @Transactional
    public BoatDto addBoatToSession(Long sessionId, AddBoatRequest request) {
        RowingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        BoatType type = BoatType.valueOf(request.getType().toUpperCase());
        validateBoatCapacity(type, request.getCapacity());

        Boat boat = Boat.builder()
                .session(session)
                .type(type)
                .capacity(request.getCapacity())
                .isBasicTrainingBoat(request.getIsBasicTrainingBoat() != null && request.getIsBasicTrainingBoat())
                .hasCoxSeat(request.getHasCoxSeat() != null && request.getHasCoxSeat())
                .currentBookings(0)
                .name(request.getName() != null ? request.getName() :
                        type.name().toLowerCase() + "-" + request.getCapacity() + "x")
                .build();

        return BoatDto.from(boatRepository.save(boat));
    }

    @Transactional
    public SessionDto approveSession(Long sessionId) {
        RowingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        session.setStatus(SessionStatus.APPROVED);
        return SessionDto.from(sessionRepository.save(session));
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        RowingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        List<Boat> boats = boatRepository.findBySessionId(sessionId);
        for (Boat boat : boats) {
            if (boat.getCurrentBookings() > 0) {
                throw new BusinessException("Cannot delete session with active bookings");
            }
        }
        boatRepository.deleteAll(boats);
        sessionRepository.delete(session);
    }

    @Transactional
    public void deleteBoat(Long boatId) {
        Boat boat = boatRepository.findById(boatId)
                .orElseThrow(() -> new ResourceNotFoundException("Boat not found"));
        if (boat.getCurrentBookings() > 0) {
            throw new BusinessException("Cannot delete boat with active bookings");
        }
        boatRepository.delete(boat);
    }

    public SessionDto getSessionWithBoats(Long sessionId) {
        RowingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        List<Boat> boats = boatRepository.findBySessionId(sessionId);
        SessionDto dto = SessionDto.from(session);
        dto.setBoats(boats.stream().map(boat -> {
            BoatDto boatDto = BoatDto.from(boat);
            var bookings = bookingRepository.findByBoatIdAndStatusNot(boat.getId(),
                    com.rowingclub.backend.enums.BookingStatus.CANCELED);
            boatDto.setBookings(bookings.stream().map(BookingDto::from).toList());
            return boatDto;
        }).toList());
        return dto;
    }

    public List<SessionDto> getApprovedUpcomingSessions() {
        return buildSessionDtos(
                sessionRepository.findByDateGreaterThanEqualAndStatus(LocalDate.now(), SessionStatus.APPROVED));
    }

    public List<SessionDto> getApprovedUpcomingSessions(Long clubId) {
        if (clubId == null) return getApprovedUpcomingSessions();
        return buildSessionDtos(sessionRepository.findByClubIdAndDateGreaterThanEqualAndStatus(
                clubId, LocalDate.now(), SessionStatus.APPROVED));
    }

    public List<SessionDto> getSessionsByDateRange(LocalDate start, LocalDate end) {
        return buildSessionDtos(sessionRepository.findByDateBetween(start, end));
    }

    public List<SessionDto> getAllSessionsByDateRange(LocalDate start, LocalDate end) {
        return buildSessionDtos(sessionRepository.findByDateBetween(start, end));
    }

    public List<SessionDto> getAllSessionsByDateRange(Long clubId, LocalDate start, LocalDate end) {
        if (clubId == null) return getAllSessionsByDateRange(start, end);
        return buildSessionDtos(sessionRepository.findByClubIdAndDateBetween(clubId, start, end));
    }

    /**
     * Assemble session DTOs (with boats and their bookings) for a list of sessions
     * using a fixed number of queries — one for all boats, one for all bookings —
     * instead of the previous per-session / per-boat N+1 fan-out.
     */
    private List<SessionDto> buildSessionDtos(List<RowingSession> sessions) {
        if (sessions.isEmpty()) return List.of();

        List<Long> sessionIds = sessions.stream().map(RowingSession::getId).toList();
        List<Boat> boats = boatRepository.findBySessionIdIn(sessionIds);

        List<Long> boatIds = boats.stream().map(Boat::getId).toList();
        Map<Long, List<BookingDto>> bookingsByBoat = boatIds.isEmpty()
                ? Map.of()
                : bookingRepository
                        .findByBoatIdInAndStatusNotFetchUser(boatIds, BookingStatus.CANCELED)
                        .stream()
                        .collect(Collectors.groupingBy(
                                b -> b.getBoat().getId(),
                                Collectors.mapping(BookingDto::from, Collectors.toList())));

        Map<Long, List<BoatDto>> boatsBySession = boats.stream().collect(Collectors.groupingBy(
                boat -> boat.getSession().getId(),
                Collectors.mapping(boat -> {
                    BoatDto dto = BoatDto.from(boat);
                    dto.setBookings(bookingsByBoat.getOrDefault(boat.getId(), List.of()));
                    return dto;
                }, Collectors.toList())));

        return sessions.stream().map(session -> {
            SessionDto dto = SessionDto.from(session);
            dto.setBoats(boatsBySession.getOrDefault(session.getId(), Collections.emptyList()));
            return dto;
        }).toList();
    }

    @Transactional
    public List<SessionDto> copyWeekSessions(LocalDate sourceWeekStart, LocalDate targetWeekStart, Club club) {
        List<SessionDto> created = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate src = sourceWeekStart.plusDays(offset);
            LocalDate tgt = targetWeekStart.plusDays(offset);
            created.addAll(copyDaySessions(src, tgt, club));
        }
        return created;
    }

    @Transactional
    public List<SessionDto> copyDaySessions(LocalDate sourceDate, LocalDate targetDate, Club club) {
        List<RowingSession> sourceSessions = club != null
                ? sessionRepository.findByClubIdAndDate(club.getId(), sourceDate)
                : sessionRepository.findByDate(sourceDate);
        List<SessionDto> created = new ArrayList<>();
        for (RowingSession source : sourceSessions) {
            RowingSession newSession = RowingSession.builder()
                    .club(source.getClub())
                    .date(targetDate)
                    .startTime(source.getStartTime())
                    .endTime(source.getEndTime())
                    .status(SessionStatus.DRAFT)
                    .build();
            newSession = sessionRepository.save(newSession);

            List<Boat> sourceBoats = boatRepository.findBySessionId(source.getId());
            for (Boat sourceBoat : sourceBoats) {
                Boat newBoat = Boat.builder()
                        .session(newSession)
                        .type(sourceBoat.getType())
                        .capacity(sourceBoat.getCapacity())
                        .isBasicTrainingBoat(sourceBoat.getIsBasicTrainingBoat())
                        .hasCoxSeat(sourceBoat.getHasCoxSeat())
                        .currentBookings(0)
                        .name(sourceBoat.getName())
                        .build();
                boatRepository.save(newBoat);
            }
            created.add(getSessionWithBoats(newSession.getId()));
        }
        return created;
    }

    @Transactional
    public void bulkDeleteSessions(List<Long> sessionIds) {
        for (Long id : sessionIds) {
            deleteSession(id);
        }
    }

    @Transactional
    public List<SessionDto> bulkApprove(List<Long> sessionIds) {
        return sessionIds.stream().map(this::approveSession).toList();
    }

    private void validateBoatCapacity(BoatType type, int capacity) {
        if (type == BoatType.COASTAL) {
            if (capacity != 1 && capacity != 2 && capacity != 4) {
                throw new BusinessException("Coastal boats must have 1, 2, or 4 seats");
            }
        } else if (type == BoatType.OLYMPIC) {
            if (capacity != 1 && capacity != 2 && capacity != 4 && capacity != 8) {
                throw new BusinessException("Olympic boats must have 1, 2, 4, or 8 seats");
            }
        }
    }
}
