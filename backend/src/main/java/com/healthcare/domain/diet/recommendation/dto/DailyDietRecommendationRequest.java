package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record DailyDietRecommendationRequest(
        @NotNull LocalDate date,
        @NotEmpty List<MealType> mealTypes,
        boolean strictAllergyMode,
        int alternativeCount
) {
    public DailyDietRecommendationRequest(LocalDate date, List<MealType> mealTypes, boolean strictAllergyMode) {
        this(date, mealTypes, strictAllergyMode, 0);
    }
}
