package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CatalogSearchParams {

    private final String query;
    private final ExerciseType exerciseType;
    private final MuscleGroup muscleGroup;
    private final boolean customOnly;

    public static CatalogSearchParams of(String query, ExerciseType exerciseType,
            MuscleGroup muscleGroup, boolean customOnly) {
        return new CatalogSearchParams(query, exerciseType, muscleGroup, customOnly);
    }
}
