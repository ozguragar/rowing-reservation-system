package com.rowingclub.backend.entity;

import com.rowingclub.backend.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isFinishedBasicTraining = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isOnSchoolTeam = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer lessonsAttended = 0;

    private String refreshToken;
}
