package com.healthcare.domain.goals.dto;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.entity.Goal.GoalStatus;
import com.healthcare.domain.goals.entity.Goal.GoalType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalResponse {

    private Long goalId;
    private GoalType goalType;
    private BigDecimal targetValue;
    private String targetUnit;
    private LocalDate targetDate;
    private BigDecimal startValue;
    private LocalDate startDate;
    private GoalStatus status;
    private BigDecimal weeklyRateTarget;
    private Integer impliedWeeksToGoal;
    private MacroTargets targets;

    public static GoalResponse from(Goal goal) {
        int impliedWeeks = 0;
        if (goal.getTargetDate() != null && goal.getStartDate() != null) {
            impliedWeeks = (int) ((goal.getTargetDate().toEpochDay() - goal.getStartDate().toEpochDay()) / 7);
        }

        return GoalResponse.builder()
                .goalId(goal.getId())
                .goalType(goal.getGoalType())
                .targetValue(goal.getTargetValue())
                .targetUnit(goal.getTargetUnit())
                .targetDate(goal.getTargetDate())
                .startValue(goal.getStartValue())
                .startDate(goal.getStartDate())
                .status(goal.getStatus())
                .weeklyRateTarget(goal.getWeeklyRateTarget())
                .impliedWeeksToGoal(impliedWeeks)
                .targets(MacroTargets.builder()
                        .calorieTarget(goal.getCalorieTarget())
                        .proteinTargetG(goal.getProteinTargetG())
                        .carbTargetG(goal.getCarbTargetG())
                        .fatTargetG(goal.getFatTargetG())
                        .build())
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MacroTargets {
        private Integer calorieTarget;
        private Integer proteinTargetG;
        private Integer carbTargetG;
        private Integer fatTargetG;
    }
}
