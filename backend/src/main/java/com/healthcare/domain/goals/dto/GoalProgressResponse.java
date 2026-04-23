package com.healthcare.domain.goals.dto;

import com.healthcare.domain.goals.entity.Goal.GoalType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalProgressResponse {

    private Long goalId;
    private GoalType goalType;
    private BigDecimal targetValue;
    private String targetUnit;
    private LocalDate targetDate;
    private LocalDate startDate;
    private BigDecimal startValue;
    private BigDecimal weeklyRateTarget;
    private BigDecimal currentValue;
    private Double percentComplete;
    private Long daysRemaining;
    private LocalDate projectedCompletionDate;
    private Boolean isOnTrack;
    private String trackingStatus;
    private String trackingColor;
    private List<GoalCheckpointResponse> checkpoints;
}
