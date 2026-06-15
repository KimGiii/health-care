package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;

public record RecommendedFoodEntry(
        Long foodCatalogId,
        String name,
        String nameKo,
        FoodCategory category,
        double servingG,
        double calories,
        double proteinG,
        double carbsG,
        double fatG,
        AllergenConfidenceLevel allergenConfidenceLevel,
        /** RECOMMENDABLE_WITH_CAUTION 상태 식품의 주의 사유. 일반 추천 식품은 null. */
        String caution
) {
    public static RecommendedFoodEntry from(DietRecommendationCandidate food, double servingG) {
        double factor = servingG / 100.0;
        return new RecommendedFoodEntry(
                food.foodCatalogId(),
                food.name(),
                food.nameKo(),
                food.category(),
                round(servingG),
                round(food.caloriesPer100g() * factor),
                round(food.proteinPer100g() * factor),
                round(food.carbsPer100g() * factor),
                round(food.fatPer100g() * factor),
                food.allergenConfidenceLevel(),
                food.caution()
        );
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
