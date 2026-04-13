package com.healthcare.domain.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateProfileRequest {

    @Size(min = 1, max = 100, message = "닉네임은 1~100자여야 합니다.")
    private String displayName;

    @DecimalMin(value = "50.0", message = "키는 50cm 이상이어야 합니다.")
    @DecimalMax(value = "300.0", message = "키는 300cm 이하여야 합니다.")
    private Double heightCm;

    @DecimalMin(value = "20.0", message = "체중은 20kg 이상이어야 합니다.")
    @DecimalMax(value = "500.0", message = "체중은 500kg 이하여야 합니다.")
    private Double weightKg;

    private String activityLevel;

    private Integer calorieTarget;
    private Integer proteinTargetG;
    private Integer carbTargetG;
    private Integer fatTargetG;
    private String fcmToken;
}
