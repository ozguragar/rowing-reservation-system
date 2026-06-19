package com.rowingclub.backend.dto;

import com.rowingclub.backend.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private Long id;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String userRole;
    private Long boatId;
    private String boatName;
    private Long sessionId;
    private LocalDate sessionDate;
    private LocalTime sessionStartTime;
    private LocalTime sessionEndTime;
    private String status;
    private Boolean isCoxSeat;
    private LocalDateTime createdAt;

    public static BookingDto from(Booking booking) {
        return BookingDto.builder()
                .id(booking.getId())
                .userId(booking.getUser().getId())
                .userFullName(booking.getUser().getFullName())
                .userEmail(booking.getUser().getEmail())
                .userRole(booking.getUser().getRole().name())
                .boatId(booking.getBoat().getId())
                .boatName(booking.getBoat().getName())
                .sessionId(booking.getSession().getId())
                .sessionDate(booking.getSession().getDate())
                .sessionStartTime(booking.getSession().getStartTime())
                .sessionEndTime(booking.getSession().getEndTime())
                .status(booking.getStatus().name())
                .isCoxSeat(booking.getIsCoxSeat())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
