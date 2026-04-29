package com.healthcare.domain.exercise.ai.dto;

import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiExerciseEstimateResponse {

    private final String exerciseName;
    private final MuscleGroup muscleGroup;
    private final ExerciseType exerciseType;
    private final Double metValue;
    private final Double confidence;
    private final String disclaimer;
    private final boolean isAiEstimated;
}
