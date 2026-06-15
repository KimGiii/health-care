package com.healthcare.domain.diet.recommendation.candidate;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;

import java.util.Objects;

/**
 * 식단 추천 엔진이 필요로 하는 식품 후보 스냅샷.
 * 엔진은 FoodCatalog 엔티티와 알러젠 태그 맵을 직접 알지 않는다.
 */
public record DietRecommendationCandidate(
        Long foodCatalogId,
        String name,
        String nameKo,
        FoodCategory category,
        double caloriesPer100g,
        double proteinPer100g,
        double carbsPer100g,
        double fatPer100g,
        long usageCount,
        long stableKey,
        AllergenConfidenceLevel allergenConfidenceLevel,
        String caution
) {
    public DietRecommendationCandidate {
        allergenConfidenceLevel = allergenConfidenceLevel == null
                ? AllergenConfidenceLevel.UNKNOWN
                : allergenConfidenceLevel;
    }

    public static DietRecommendationCandidate from(
            FoodCatalog food,
            AllergenConfidenceLevel allergenConfidenceLevel
    ) {
        return new DietRecommendationCandidate(
                food.getId(),
                food.getName(),
                food.getNameKo(),
                food.getCategory(),
                orZero(food.getCaloriesPer100g()),
                orZero(food.getProteinPer100g()),
                orZero(food.getCarbsPer100g()),
                orZero(food.getFatPer100g()),
                food.getUsageCount() != null ? food.getUsageCount() : 0L,
                stableFoodKey(food),
                allergenConfidenceLevel,
                food.curation().cautionForResponse()
        );
    }

    private static long stableFoodKey(FoodCatalog food) {
        if (food.getId() != null) {
            return food.getId();
        }
        return Objects.hash(food.getSource(), food.getFoodCode(), food.getName(), food.getNameKo());
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
