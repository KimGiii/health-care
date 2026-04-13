package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import com.healthcare.domain.exercise.entity.ExerciseSet;
import com.healthcare.domain.exercise.entity.ExerciseSet.SetType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SetDetailResponse {

    private final Long setId;
    private final Long exerciseCatalogId;
    private final String exerciseName;
    private final String exerciseNameKo;
    private final MuscleGroup muscleGroup;
    private final Short setNumber;
    private final SetType setType;
    private final Double weightKg;
    private final Short reps;
    private final Integer durationSeconds;
    private final Double distanceM;
    private final Short restSeconds;
    private final boolean personalRecord;

    public static SetDetailResponse from(ExerciseSet set, String exerciseName,
            String exerciseNameKo, MuscleGroup muscleGroup) {
        return SetDetailResponse.builder()
                .setId(set.getId())
                .exerciseCatalogId(set.getExerciseCatalogId())
                .exerciseName(exerciseName)
                .exerciseNameKo(exerciseNameKo)
                .muscleGroup(muscleGroup)
                .setNumber(set.getSetNumber())
                .setType(set.getSetType())
                .weightKg(set.getWeightKg())
                .reps(set.getReps())
                .durationSeconds(set.getDurationSeconds())
                .distanceM(set.getDistanceM())
                .restSeconds(set.getRestSeconds())
                .personalRecord(set.getIsPersonalRecord())
                .build();
    }
}
