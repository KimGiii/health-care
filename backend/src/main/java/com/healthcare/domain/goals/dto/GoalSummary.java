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
public class GoalSummary {

    private Long goalId;
    private GoalType goalType;
    private BigDecimal targetValue;
    private String targetUnit;
    private LocalDate targetDate;
    private LocalDate startDate;
    private GoalStatus status;
    private Double percentComplete;

    public static GoalSummary from(Goal goal) {
        return GoalSummary.builder()
                .goalId(goal.getId())
                .goalType(goal.getGoalType())
                .targetValue(goal.getTargetValue())
                .targetUnit(goal.getTargetUnit())
                .targetDate(goal.getTargetDate())
                .startDate(goal.getStartDate())
                .status(goal.getStatus())
                .percentComplete(null) // 실측값 필요 시 별도 조회
                .build();
    }
}
