package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomExerciseRequest {

    @NotBlank(message = "운동 이름은 필수입니다.")
    private String name;

    private String nameKo;

    @NotNull(message = "근육 그룹은 필수입니다.")
    private MuscleGroup muscleGroup;

    @NotNull(message = "운동 타입은 필수입니다.")
    private ExerciseType exerciseType;

    private Double metValue;
}
