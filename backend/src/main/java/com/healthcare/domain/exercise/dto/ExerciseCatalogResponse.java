package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseCatalog;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExerciseCatalogResponse {

    private final Long id;
    private final String name;
    private final String nameKo;
    private final MuscleGroup muscleGroup;
    private final ExerciseType exerciseType;
    private final Double metValue;
    private final boolean custom;
    private final Long createdByUserId;

    public static ExerciseCatalogResponse from(ExerciseCatalog catalog) {
        return ExerciseCatalogResponse.builder()
                .id(catalog.getId())
                .name(catalog.getName())
                .nameKo(catalog.getNameKo())
                .muscleGroup(catalog.getMuscleGroup())
                .exerciseType(catalog.getExerciseType())
                .metValue(catalog.getMetValue())
                .custom(catalog.getIsCustom())
                .createdByUserId(catalog.getCreatedByUserId())
                .build();
    }
}
