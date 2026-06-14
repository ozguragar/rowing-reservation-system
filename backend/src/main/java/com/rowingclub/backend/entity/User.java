package com.rowingclub.backend.entity;

import com.rowingclub.backend.enums.MemberType;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    private MemberType memberType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isFinishedBasicTraining = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isOnSchoolTeam = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer lessonsAttended = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCox = false;

    private String refreshToken;
}
