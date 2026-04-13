package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseSet.SetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSetRequest {

    @NotNull
    private Long exerciseCatalogId;

    @NotNull @Positive
    private Short setNumber;

    @NotNull
    private SetType setType;

    // WEIGHTED / BODYWEIGHT
    private Double weightKg;
    private Short reps;

    // CARDIO
    private Integer durationSeconds;
    private Double distanceM;

    // 공통 선택 필드
    private Short restSeconds;
    private String notes;
}
