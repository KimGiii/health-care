package com.healthcare.domain.user.dto;

import com.healthcare.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
public class UserProfileResponse {

    private Long id;
    private String email;
    private String displayName;
    private String sex;
    private LocalDate dateOfBirth;
    private Double heightCm;
    private Double weightKg;
    private String activityLevel;
    private String locale;
    private String timezone;
    private Integer calorieTarget;
    private Integer proteinTargetG;
    private Integer carbTargetG;
    private Integer fatTargetG;
    private OffsetDateTime createdAt;
    private boolean onboardingCompleted;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .sex(user.getSex() != null ? user.getSex().name() : null)
            .dateOfBirth(user.getDateOfBirth())
            .heightCm(user.getHeightCm())
            .weightKg(user.getWeightKg())
            .activityLevel(user.getActivityLevel() != null ? user.getActivityLevel().name() : null)
            .locale(user.getLocale())
            .timezone(user.getTimezone())
            .calorieTarget(user.getCalorieTarget())
            .proteinTargetG(user.getProteinTargetG())
            .carbTargetG(user.getCarbTargetG())
            .fatTargetG(user.getFatTargetG())
            .createdAt(user.getCreatedAt())
            .onboardingCompleted(user.isOnboardingCompleted())
            .build();
    }
}
