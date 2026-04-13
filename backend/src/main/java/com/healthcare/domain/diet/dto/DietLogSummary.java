package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class DietLogSummary {

    private final Long dietLogId;
    private final LocalDate logDate;
    private final MealType mealType;
    private final Double totalCalories;
    private final Double totalProteinG;
    private final Double totalCarbsG;
    private final Double totalFatG;

    public static DietLogSummary from(DietLog log) {
        return DietLogSummary.builder()
                .dietLogId(log.getId())
                .logDate(log.getLogDate())
                .mealType(log.getMealType())
                .totalCalories(log.getTotalCalories())
                .totalProteinG(log.getTotalProteinG())
                .totalCarbsG(log.getTotalCarbsG())
                .totalFatG(log.getTotalFatG())
                .build();
    }
}
