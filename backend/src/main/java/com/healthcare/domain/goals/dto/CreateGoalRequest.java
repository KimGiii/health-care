package com.healthcare.domain.goals.dto;

import com.healthcare.domain.goals.entity.Goal.GoalType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoalRequest {

    @NotNull
    private GoalType goalType;

    private BigDecimal targetValue;
    private String targetUnit;

    @NotNull
    private LocalDate targetDate;

    private BigDecimal startValue;
    private BigDecimal weeklyRateTarget;
}
