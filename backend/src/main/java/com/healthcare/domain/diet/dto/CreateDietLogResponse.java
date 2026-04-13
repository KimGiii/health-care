package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CreateDietLogResponse {

    private final Long dietLogId;
    private final LocalDate logDate;
    private final MealType mealType;
    private final int entryCount;
    private final Double totalCalories;
    private final Double totalProteinG;
    private final Double totalCarbsG;
    private final Double totalFatG;
}
