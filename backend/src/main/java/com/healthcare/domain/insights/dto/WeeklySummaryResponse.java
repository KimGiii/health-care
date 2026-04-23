package com.healthcare.domain.insights.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class WeeklySummaryResponse {

    private LocalDate weekStart;
    private LocalDate weekEnd;
    private int weekOffset;

    // 운동
    private int exerciseSessionCount;
    private int totalExerciseMinutes;
    private Double totalCaloriesBurned;

    // 식단
    private int dietLogCount;
    private Double avgDailyCalories;
    private Double avgDailyProteinG;

    // 신체
    private Double latestWeightKg;
    private Double latestBodyFatPct;
    private Double weightChangeKg;

    // 목표
    private Double activeGoalPercentComplete;
    private String activeGoalTrackingStatus;
}
