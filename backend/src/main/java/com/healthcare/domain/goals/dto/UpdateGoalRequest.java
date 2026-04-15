package com.healthcare.domain.goals.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGoalRequest {

    private LocalDate targetDate;
    private BigDecimal targetValue;
    private BigDecimal weeklyRateTarget;
}
