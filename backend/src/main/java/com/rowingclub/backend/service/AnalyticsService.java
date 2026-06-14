package com.rowingclub.backend.service;

import com.rowingclub.backend.dto.AnalyticsDto;
import com.rowingclub.backend.entity.Boat;
import com.rowingclub.backend.entity.RowingSession;
import com.rowingclub.backend.repository.BoatRepository;
import com.rowingclub.backend.repository.RowingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final RowingSessionRepository sessionRepository;
    private final BoatRepository boatRepository;

    public List<AnalyticsDto> getOccupancyLast7Days() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(7);
        List<RowingSession> sessions = sessionRepository.findByDateBetween(start, end);

        List<AnalyticsDto> analytics = new ArrayList<>();
        for (RowingSession session : sessions) {
            List<Boat> boats = boatRepository.findBySessionId(session.getId());
            int totalCapacity = boats.stream().mapToInt(Boat::getCapacity).sum();
            int totalBooked = boats.stream().mapToInt(Boat::getCurrentBookings).sum();
            double occupancy = totalCapacity > 0 ? (double) totalBooked / totalCapacity * 100 : 0;

            analytics.add(AnalyticsDto.builder()
                    .sessionId(session.getId())
                    .date(session.getDate())
                    .sessionTime(session.getStartTime() + " - " + session.getEndTime())
                    .totalCapacity(totalCapacity)
                    .totalBooked(totalBooked)
                    .occupancyPercentage(Math.round(occupancy * 100.0) / 100.0)
                    .build());
        }
        return analytics;
    }
}
