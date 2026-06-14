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
    private Long clubId;
    private String fullName;
    private String email;
    private String role;
    private String memberType;
    private Boolean isFinishedBasicTraining;
    private Boolean isOnSchoolTeam;
    private Integer lessonsAttended;
    private Boolean isCox;
    private BigDecimal creditBalance;
    private LocalDateTime earliestCreditExpiration;
    private String clubName;
    private Boolean featureAvailabilityModule;
    private Boolean featureCancellationRequests;
    private Boolean featureAutoScheduler;
    private Boolean featureShowBookedMembers;

    public static UserDto from(User user) {
        UserDtoBuilder builder = UserDto.builder()
                .id(user.getId())
                .clubId(user.getClub() != null ? user.getClub().getId() : null)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .memberType(user.getMemberType() != null ? user.getMemberType().name() : null)
                .isFinishedBasicTraining(user.getIsFinishedBasicTraining())
                .isOnSchoolTeam(user.getIsOnSchoolTeam())
                .lessonsAttended(user.getLessonsAttended())
                .isCox(user.getIsCox());
        if (user.getClub() != null) {
            builder.clubName(user.getClub().getName())
                    .featureAvailabilityModule(user.getClub().getFeatureAvailabilityModule())
                    .featureCancellationRequests(user.getClub().getFeatureCancellationRequests())
                    .featureAutoScheduler(user.getClub().getFeatureAutoScheduler())
                    .featureShowBookedMembers(user.getClub().getFeatureShowBookedMembers());
        }
        return builder.build();
    }
}
