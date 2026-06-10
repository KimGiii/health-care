package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.RecommendationStatus;

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
    public static RecommendedFoodEntry from(FoodCatalog food, double servingG, AllergenConfidenceLevel confidence) {
        double factor = servingG / 100.0;
        String caution = food.getRecommendationStatus() == RecommendationStatus.RECOMMENDABLE_WITH_CAUTION
                ? food.getRecommendationReason()
                : null;
        return new RecommendedFoodEntry(
                food.getId(),
                food.getName(),
                food.getNameKo(),
                food.getCategory(),
                round(servingG),
                round(food.getCaloriesPer100g() * factor),
                round(orZero(food.getProteinPer100g()) * factor),
                round(orZero(food.getCarbsPer100g()) * factor),
                round(orZero(food.getFatPer100g()) * factor),
                confidence,
                caution
        );
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
