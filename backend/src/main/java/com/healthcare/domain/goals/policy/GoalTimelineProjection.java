package com.healthcare.domain.goals.policy;

import java.time.LocalDate;

public record GoalTimelineProjection(
        LocalDate requestedTargetDate,
        LocalDate projectedTargetDate,
        boolean recalculationDue
) {
}
