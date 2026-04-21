package com.healthcare.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sex sex;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 20)
    private ActivityLevel activityLevel;

    @Column(name = "locale", length = 10, nullable = false)
    @Builder.Default
    private String locale = "ko-KR";

    @Column(name = "timezone", length = 64, nullable = false)
    @Builder.Default
    private String timezone = "Asia/Seoul";

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private boolean onboardingCompleted = false;

    @Column(name = "calorie_target")
    private Integer calorieTarget;

    @Column(name = "protein_target_g")
    private Integer proteinTargetG;

    @Column(name = "carb_target_g")
    private Integer carbTargetG;

    @Column(name = "fat_target_g")
    private Integer fatTargetG;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void updateProfile(String displayName, LocalDate dateOfBirth, Double heightCm, Double weightKg,
                              ActivityLevel activityLevel, Sex sex, String locale, String timezone) {
        if (displayName != null) this.displayName = displayName;
        if (dateOfBirth != null) this.dateOfBirth = dateOfBirth;
        if (heightCm != null) this.heightCm = heightCm;
        if (weightKg != null) this.weightKg = weightKg;
        if (activityLevel != null) this.activityLevel = activityLevel;
        if (sex != null) this.sex = sex;
        if (locale != null) this.locale = locale;
        if (timezone != null) this.timezone = timezone;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    public void updateTargets(Integer calorieTarget, Integer proteinTargetG, Integer carbTargetG, Integer fatTargetG) {
        if (calorieTarget != null) this.calorieTarget = calorieTarget;
        if (proteinTargetG != null) this.proteinTargetG = proteinTargetG;
        if (carbTargetG != null) this.carbTargetG = carbTargetG;
        if (fatTargetG != null) this.fatTargetG = fatTargetG;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public enum Sex {
        MALE, FEMALE, OTHER
    }

    public enum ActivityLevel {
        SEDENTARY, LIGHTLY_ACTIVE, MODERATELY_ACTIVE, VERY_ACTIVE, EXTRA_ACTIVE
    }
}
