package com.rowingclub.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_availability", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "session_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private RowingSession session;
}
