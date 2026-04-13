package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class DietLogDetailResponse {

    private final Long dietLogId;
    private final LocalDate logDate;
    private final MealType mealType;
    private final Double totalCalories;
    private final Double totalProteinG;
    private final Double totalCarbsG;
    private final Double totalFatG;
    private final String notes;
    private final List<FoodEntryResponse> entries;

    public static DietLogDetailResponse from(DietLog log, List<FoodEntryResponse> entries) {
        return DietLogDetailResponse.builder()
                .dietLogId(log.getId())
                .logDate(log.getLogDate())
                .mealType(log.getMealType())
                .totalCalories(log.getTotalCalories())
                .totalProteinG(log.getTotalProteinG())
                .totalCarbsG(log.getTotalCarbsG())
                .totalFatG(log.getTotalFatG())
                .notes(log.getNotes())
                .entries(entries)
                .build();
    }
}
