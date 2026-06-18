package com.healthcare.domain.diet.recommendation.candidate;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;

import java.util.List;
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
        String caution,
        boolean macroDataComplete,
        List<ServingOptionSnapshot> servingOptions,
        boolean hasVerifiedServingOptions
) {
    public DietRecommendationCandidate {
        allergenConfidenceLevel = allergenConfidenceLevel == null
                ? AllergenConfidenceLevel.UNKNOWN
                : allergenConfidenceLevel;
        servingOptions = servingOptions == null ? List.of() : List.copyOf(servingOptions);
    }

    public static DietRecommendationCandidate from(
            FoodCatalog food,
            AllergenConfidenceLevel allergenConfidenceLevel,
            List<FoodServingOption> options
    ) {
        List<ServingOptionSnapshot> snapshots = options.stream()
                .sorted(java.util.Comparator.comparingInt(FoodServingOption::getSortOrder))
                .map(ServingOptionSnapshot::from)
                .toList();
        boolean hasVerified = options.stream().anyMatch(FoodServingOption::isVerified);
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
                food.curation().cautionForResponse(),
                food.getCaloriesPer100g() != null
                        && food.getProteinPer100g() != null
                        && food.getCarbsPer100g() != null
                        && food.getFatPer100g() != null,
                snapshots,
                hasVerified
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
