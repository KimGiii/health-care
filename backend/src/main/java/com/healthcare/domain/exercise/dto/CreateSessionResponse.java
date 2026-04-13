package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseSession.CalorieEstimateMethod;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class CreateSessionResponse {

    private final Long sessionId;
    private final LocalDate sessionDate;
    private final Integer durationMinutes;
    private final Double totalVolumeKg;
    private final Double caloriesBurned;
    private final CalorieEstimateMethod calorieEstimateMethod;
    private final int setCount;
    private final List<PersonalRecordInfo> newPersonalRecords;
}
