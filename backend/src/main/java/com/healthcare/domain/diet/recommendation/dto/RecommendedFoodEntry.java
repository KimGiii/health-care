package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;
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
        String caution,
        /** 낱개 식품의 개수 라벨("2개") 또는 제공량 라벨("1.5회 제공량"). 매칭 옵션 없으면 null. iOS 표시용. */
        String servingLabel
) {
    /** 기존 11-인자 호출 호환(servingLabel 미지정 = null). */
    public RecommendedFoodEntry(
            Long foodCatalogId, String name, String nameKo, FoodCategory category,
            double servingG, double calories, double proteinG, double carbsG, double fatG,
            AllergenConfidenceLevel allergenConfidenceLevel, String caution) {
        this(foodCatalogId, name, nameKo, category, servingG, calories, proteinG, carbsG, fatG,
                allergenConfidenceLevel, caution, null);
    }

    public static RecommendedFoodEntry from(DietRecommendationCandidate food, double servingG) {
        double factor = servingG / 100.0;
        String servingLabel = food.servingOptions().stream()
                .filter(option -> Math.abs(option.equivalentG() - servingG) < 0.5)
                .map(ServingOptionSnapshot::labelKo)
                .findFirst()
                .orElse(null);
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
                food.caution(),
                servingLabel);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
