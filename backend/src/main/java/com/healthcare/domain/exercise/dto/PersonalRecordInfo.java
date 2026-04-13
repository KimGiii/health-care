package com.healthcare.domain.exercise.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PersonalRecordInfo {
    private final Long exerciseCatalogId;
    private final String exerciseName;
    private final String exerciseNameKo;
    private final Double weightKg;
    private final Short reps;
}
