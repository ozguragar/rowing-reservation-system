package com.rowingclub.backend.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubDto {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private Boolean featureAvailabilityModule;
    private Boolean featureCancellationRequests;
    private Boolean featureAutoScheduler;
    private Boolean featureShowBookedMembers;
}
