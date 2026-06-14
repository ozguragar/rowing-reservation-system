package com.rowingclub.backend.dto;

import com.rowingclub.backend.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
    private String status;
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
                .status(booking.getStatus().name())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
