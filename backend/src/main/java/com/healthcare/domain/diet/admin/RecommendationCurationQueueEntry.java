package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;

public record RecommendationCurationQueueEntry(
        Long foodCatalogId,
        FoodCatalogSource source,
        String foodCode,
        String name,
        FoodCategory category,
        long usageCount,
        boolean macroComplete,
        boolean hasVerifiedServingOption,
        boolean hasAllergenTags,
        double priorityScore
) {
    public boolean engineReadyCandidate() {
        return macroComplete && hasVerifiedServingOption;
    }
}
