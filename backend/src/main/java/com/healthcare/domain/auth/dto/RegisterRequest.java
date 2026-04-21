package com.healthcare.domain.auth.dto;

import com.healthcare.domain.goals.entity.Goal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class RegisterRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 1, max = 100, message = "닉네임은 1~100자여야 합니다.")
    private String displayName;

    private String sex;
    private LocalDate dateOfBirth;

    @DecimalMin(value = "50.0", message = "키는 50cm 이상이어야 합니다.")
    @DecimalMax(value = "300.0", message = "키는 300cm 이하여야 합니다.")
    private Double heightCm;

    @DecimalMin(value = "20.0", message = "체중은 20kg 이상이어야 합니다.")
    @DecimalMax(value = "500.0", message = "체중은 500kg 이하여야 합니다.")
    private Double weightKg;

    private String activityLevel;
    private Goal.GoalType goalType;
    private String locale;
    private String timezone;
}
