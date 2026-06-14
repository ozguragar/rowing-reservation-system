package com.rowingclub.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clubs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private Boolean featureAvailabilityModule = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featureCancellationRequests = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featureAutoScheduler = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featureShowBookedMembers = true;
}
