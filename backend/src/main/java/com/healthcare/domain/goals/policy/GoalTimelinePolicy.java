package com.healthcare.domain.goals.policy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class GoalTimelinePolicy {

    public GoalTimelineProjection evaluate(GoalTimelineInput input) {
        long weeksNeeded = (long) Math.ceil(input.remainingMagnitude() / input.safeWeeklyRate());
        return new GoalTimelineProjection(
                input.requestedTargetDate(),
                input.asOfDate().plusWeeks(weeksNeeded),
                isRecalculationDue(input.asOfDate(), input.lastRecalculatedOn())
        );
    }

    private boolean isRecalculationDue(LocalDate asOfDate, LocalDate lastRecalculatedOn) {
        if (lastRecalculatedOn == null) {
            return true;
        }
        LocalDate currentWeek = asOfDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousWeek = lastRecalculatedOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return !currentWeek.equals(previousWeek);
    }
}
