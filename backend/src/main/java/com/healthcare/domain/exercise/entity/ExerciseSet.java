package com.healthcare.domain.exercise.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "exercise_sets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ExerciseSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "exercise_catalog_id", nullable = false)
    private Long exerciseCatalogId;

    @Column(name = "set_number", nullable = false)
    private Short setNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "set_type", nullable = false, length = 15)
    private SetType setType;

    // WEIGHTED / BODYWEIGHT
    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "reps")
    private Short reps;

    // CARDIO
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "distance_m")
    private Double distanceM;

    // 공통 선택 필드
    @Column(name = "rest_seconds")
    private Short restSeconds;

    @Column(name = "is_personal_record", nullable = false)
    private Boolean isPersonalRecord;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (isPersonalRecord == null) isPersonalRecord = false;
    }

    public void markAsPersonalRecord() {
        this.isPersonalRecord = true;
    }

    public enum SetType {
        WEIGHTED, CARDIO, BODYWEIGHT
    }
}
