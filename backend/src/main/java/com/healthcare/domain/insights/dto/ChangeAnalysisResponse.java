package com.healthcare.domain.insights.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ChangeAnalysisResponse {

    private LocalDate fromDate;
    private LocalDate toDate;

    // 신체 변화 델타
    private Double weightChangeKg;
    private Double bodyFatPctChange;
    private Double muscleMassChangeKg;
    private Double bmiChange;
    private Double waistChangeCm;
    private Double chestChangeCm;

    // 기간 내 운동 요약
    private int exerciseSessionCount;
    private int totalExerciseMinutes;

    // 스냅샷
    private BodySnapshot fromSnapshot;
    private BodySnapshot toSnapshot;

    @Getter
    @Builder
    public static class BodySnapshot {
        private LocalDate measuredAt;
        private Double weightKg;
        private Double bodyFatPct;
        private Double muscleMassKg;
        private Double bmi;
        private Double waistCm;
        private Double chestCm;
    }
}
