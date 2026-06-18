package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.nutrition.dto.NutritionTargets;

import java.time.LocalDate;
import java.util.List;

public record DailyDietRecommendationResponse(
        LocalDate date,
        NutritionTargets targets,
        NutritionTargets remainingTargets,
        List<DietRestrictionResponse> appliedRestrictions,
        List<RecommendedMeal> meals,
        NutrientSummary totalNutrients,
        String failureReason,
        boolean strictAllergyMode,
        String disclaimer,
        List<List<RecommendedMeal>> alternatives,
        Long snapshotId
) {
    public boolean succeeded() {
        return failureReason == null;
    }

    public record NutrientSummary(
            double totalCalories,
            double totalProteinG,
            double totalCarbsG,
            double totalFatG
    ) {}
}
