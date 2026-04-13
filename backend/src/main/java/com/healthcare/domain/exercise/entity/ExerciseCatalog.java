package com.healthcare.domain.exercise.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;

@Entity
@Table(name = "exercise_catalog")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ExerciseCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "name_ko", length = 150)
    private String nameKo;

    @Enumerated(EnumType.STRING)
    @Column(name = "muscle_group", nullable = false, length = 50)
    private MuscleGroup muscleGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_type", nullable = false, length = 20)
    private ExerciseType exerciseType;

    @Column(name = "met_value")
    private Double metValue;

    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

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

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public enum MuscleGroup {
        CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS,
        CORE, QUADRICEPS, HAMSTRINGS, GLUTES, CALVES,
        FULL_BODY, CARDIO, OTHER
    }

    public enum ExerciseType {
        STRENGTH, CARDIO, BODYWEIGHT, FLEXIBILITY, SPORTS
    }
}
