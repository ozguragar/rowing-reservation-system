package com.rowingclub.backend.entity;

import com.rowingclub.backend.enums.BoatType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "boats")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Boat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private RowingSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoatType type;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isBasicTrainingBoat = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer currentBookings = 0;

    @Version
    private Long version;

    private String name;
}
