package com.healthcare.domain.goals.policy;

import java.time.LocalDate;

public record GoalTimelineInput(
        LocalDate asOfDate,
        LocalDate requestedTargetDate,
        double remainingMagnitude,
        double safeWeeklyRate,
        LocalDate lastRecalculatedOn
) {
    public GoalTimelineInput {
        if (asOfDate == null) {
            throw new IllegalArgumentException("asOfDate is required");
        }
        if (!Double.isFinite(remainingMagnitude) || remainingMagnitude < 0.0) {
            throw new IllegalArgumentException("remainingMagnitude must be non-negative and finite");
        }
        if (!Double.isFinite(safeWeeklyRate) || safeWeeklyRate <= 0.0) {
            throw new IllegalArgumentException("safeWeeklyRate must be positive and finite");
        }
    }
}
