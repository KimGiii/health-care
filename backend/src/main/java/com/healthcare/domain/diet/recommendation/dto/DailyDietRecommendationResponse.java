package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.nutrition.dto.NutritionTargets;

import java.time.LocalDate;
import java.util.List;

public record DailyDietRecommendationResponse(
        LocalDate date,
        NutritionTargets targets,
        List<DietRestrictionResponse> appliedRestrictions,
        List<RecommendedMeal> meals,
        NutrientSummary totalNutrients,
        boolean strictAllergyMode,
        String disclaimer
) {
    public record NutrientSummary(
            double totalCalories,
            double totalProteinG,
            double totalCarbsG,
            double totalFatG
    ) {}
}
