package com.rowingclub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDto {
    private Long sessionId;
    private LocalDate date;
    private String sessionTime;
    private int totalCapacity;
    private int totalBooked;
    private double occupancyPercentage;
}
