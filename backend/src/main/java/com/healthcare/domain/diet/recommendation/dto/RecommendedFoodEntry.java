package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;

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
        AllergenConfidenceLevel allergenConfidenceLevel
) {
    public static RecommendedFoodEntry from(FoodCatalog food, double servingG, AllergenConfidenceLevel confidence) {
        double factor = servingG / 100.0;
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
                confidence
        );
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
