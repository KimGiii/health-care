package com.healthcare.domain.diet.recommendation.candidate;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;

import java.util.Comparator;
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

    private static final double DEFAULT_SERVING_G = 100.0;
    private static final double SERVING_STEP_G = 25.0;
    private static final double MIN_SERVING_G = 25.0;
    private static final double MAX_SERVING_G = 500.0;

    /**
     * 목표 칼로리에 맞는 제공량(g)을 고른다.
     * 검증된 제공량 옵션이 있으면 임의 g 대신 목표량에 가장 가까운 검증 옵션을 사용하고,
     * 없으면 25g 단위 반올림 fallback을 쓴다. (ADR-0002 §5)
     */
    public double chooseServingGramsFor(double targetCalories) {
        if (caloriesPer100g <= 0) {
            return DEFAULT_SERVING_G;
        }
        double rawGrams = targetCalories / caloriesPer100g * 100.0;
        return servingOptions.stream()
                .filter(ServingOptionSnapshot::verified)
                .min(Comparator.comparingDouble(o -> Math.abs(o.equivalentG() - rawGrams)))
                .map(ServingOptionSnapshot::equivalentG)
                .orElseGet(() -> fallbackGrams(rawGrams));
    }

    private static double fallbackGrams(double rawGrams) {
        double rounded = Math.round(rawGrams / SERVING_STEP_G) * SERVING_STEP_G;
        return Math.max(MIN_SERVING_G, Math.min(MAX_SERVING_G, rounded));
    }

    /**
     * 추천이 선택할 수 있는 검증된 제공량(g) 후보. 공식 1회 제공량 multiplier·개수·g-step이
     * 여기에 전개되어 있다. 비어 있으면 제공량 근거가 없는 식품이므로 추천 대상이 아니다(§7.1).
     */
    public List<Double> verifiedServingGramOptions() {
        return servingOptions.stream()
                .filter(ServingOptionSnapshot::verified)
                .map(ServingOptionSnapshot::equivalentG)
                .distinct()
                .sorted()
                .toList();
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
