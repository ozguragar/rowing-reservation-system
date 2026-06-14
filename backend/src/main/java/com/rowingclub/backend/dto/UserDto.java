package com.rowingclub.backend.dto;

import com.rowingclub.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private Boolean isFinishedBasicTraining;
    private Boolean isOnSchoolTeam;
    private Integer lessonsAttended;
    private BigDecimal creditBalance;
    private LocalDateTime earliestCreditExpiration;

    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isFinishedBasicTraining(user.getIsFinishedBasicTraining())
                .isOnSchoolTeam(user.getIsOnSchoolTeam())
                .lessonsAttended(user.getLessonsAttended())
                .build();
    }
}
