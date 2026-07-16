package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;

public record RecommendationCurationQueueEntry(
        Long foodCatalogId,
        FoodCatalogSource source,
        String foodCode,
        String name,
        FoodCategory category,
        RecommendationStatus recommendationStatus,
        long usageCount,
        Double caloriesPer100g,
        Double proteinPer100g,
        double proteinGPer100Kcal,
        String curationLane,
        boolean macroComplete,
        boolean hasVerifiedServingOption,
        boolean hasAllergenTags,
        boolean hasVerifiedAllergenProfile,
        boolean allergenProfileGap,
        double priorityScore
) {
    public boolean engineReadyCandidate() {
        return macroComplete && hasVerifiedServingOption;
    }
}
