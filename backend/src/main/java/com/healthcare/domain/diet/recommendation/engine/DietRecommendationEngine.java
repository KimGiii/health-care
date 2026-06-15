package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;


/**
 * 규칙 기반 하루 식단 추천 엔진 — 순수 로직, 사이드이펙트 없음.
 * 외부 API 호출 없이 내부 데이터만 사용.
 */
@Component
public class DietRecommendationEngine {

    private static final Map<MealType, Double> BASE_RATIOS = Map.of(
            MealType.BREAKFAST, 0.25,
            MealType.LUNCH, 0.35,
            MealType.DINNER, 0.30,
            MealType.SNACK, 0.10
    );

    private static final int MAX_ITEMS_PER_MAIN_MEAL = 3;
    private static final int MAX_ITEMS_PER_SNACK = 2;

    // 끼니별 선호 카테고리 우선순위
    private static final Map<MealType, List<FoodCategory>> PREFERRED_CATEGORIES = Map.of(
            MealType.BREAKFAST, List.of(FoodCategory.GRAIN, FoodCategory.DAIRY, FoodCategory.FRUIT, FoodCategory.PROTEIN_SOURCE),
            MealType.LUNCH, List.of(FoodCategory.PROTEIN_SOURCE, FoodCategory.GRAIN, FoodCategory.VEGETABLE),
            MealType.DINNER, List.of(FoodCategory.PROTEIN_SOURCE, FoodCategory.VEGETABLE, FoodCategory.GRAIN),
            MealType.SNACK, List.of(FoodCategory.FRUIT, FoodCategory.DAIRY, FoodCategory.PROCESSED)
    );

    /**
     * 끼니 구성에 따라 총 칼로리를 비율로 분배한다.
     * 선택된 끼니의 기본 비율을 합이 100%가 되도록 정규화.
     */
    public Map<MealType, Double> distributeCalories(int totalCalories, List<MealType> selectedMeals) {
        double totalRatio = selectedMeals.stream()
                .mapToDouble(m -> BASE_RATIOS.getOrDefault(m, 0.0))
                .sum();

        Map<MealType, Double> distribution = new LinkedHashMap<>();
        for (MealType meal : selectedMeals) {
            double ratio = BASE_RATIOS.getOrDefault(meal, 0.0) / totalRatio;
            distribution.put(meal, Math.round(totalCalories * ratio * 10.0) / 10.0);
        }
        return distribution;
    }

    /**
     * 전체 하루 식단 추천을 생성한다.
     */
    public List<RecommendedMeal> recommend(
            LocalDate date,
            NutritionTargets targets,
            List<MealType> selectedMeals,
            List<DietRecommendationCandidate> filteredCandidates
    ) {
        Map<MealType, Double> calorieDistribution = distributeCalories(targets.calorieTarget(), selectedMeals);
        Set<Long> usedFoodIds = new HashSet<>();

        List<RecommendedMeal> meals = new ArrayList<>();
        for (MealType mealType : selectedMeals) {
            double targetCal = calorieDistribution.getOrDefault(mealType, 0.0);

            List<DietRecommendationCandidate> mealCandidates = sortByScore(filteredCandidates, usedFoodIds, date);
            List<RecommendedFoodEntry> items = selectItemsForMeal(
                    mealType, mealCandidates, targetCal);

            items.stream().map(RecommendedFoodEntry::foodCatalogId).forEach(usedFoodIds::add);

            double totalCal = items.stream().mapToDouble(RecommendedFoodEntry::calories).sum();
            double totalProtein = items.stream().mapToDouble(RecommendedFoodEntry::proteinG).sum();
            double totalCarbs = items.stream().mapToDouble(RecommendedFoodEntry::carbsG).sum();
            double totalFat = items.stream().mapToDouble(RecommendedFoodEntry::fatG).sum();

            meals.add(new RecommendedMeal(mealType, targetCal, round(totalCal),
                    round(totalProtein), round(totalCarbs), round(totalFat), items));
        }
        return meals;
    }

    public List<RecommendedMeal> recommend(
            NutritionTargets targets,
            List<MealType> selectedMeals,
            List<DietRecommendationCandidate> filteredCandidates
    ) {
        return recommend(LocalDate.now(), targets, selectedMeals, filteredCandidates);
    }

    // ─── 내부 로직 ───

    private List<RecommendedFoodEntry> selectItemsForMeal(
            MealType mealType,
            List<DietRecommendationCandidate> candidates,
            double targetCalories
    ) {
        if (candidates.isEmpty()) return List.of();

        int maxItems = mealType == MealType.SNACK ? MAX_ITEMS_PER_SNACK : MAX_ITEMS_PER_MAIN_MEAL;
        List<FoodCategory> preferredCategories = PREFERRED_CATEGORIES.getOrDefault(mealType, List.of());

        // 선호 카테고리별로 1개씩 선택 (있는 경우)
        List<DietRecommendationCandidate> selected = new ArrayList<>();
        Set<FoodCategory> usedCategories = new HashSet<>();

        for (FoodCategory preferredCat : preferredCategories) {
            if (selected.size() >= maxItems) break;
            candidates.stream()
                    .filter(f -> f.category() == preferredCat && !usedCategories.contains(f.category()))
                    .findFirst()
                    .ifPresent(f -> {
                        selected.add(f);
                        usedCategories.add(f.category());
                    });
        }

        // 선호 카테고리에서 maxItems 미달 시 나머지에서 보충
        if (selected.size() < maxItems) {
            candidates.stream()
                    .filter(f -> !selected.contains(f))
                    .limit(maxItems - selected.size())
                    .forEach(selected::add);
        }

        if (selected.isEmpty()) return List.of();

        // 칼로리 분배: 각 식품에 균등 분배
        double perItemCalories = targetCalories / selected.size();
        List<RecommendedFoodEntry> items = new ArrayList<>();
        for (DietRecommendationCandidate food : selected) {
            double servingG = calculateServing(food, perItemCalories);
            items.add(RecommendedFoodEntry.from(food, servingG));
        }
        return items;
    }

    /** 목표 칼로리에 맞는 제공량(g)을 계산한다. 25g 단위로 반올림. */
    private double calculateServing(DietRecommendationCandidate food, double targetCalories) {
        if (food.caloriesPer100g() <= 0) return 100.0;
        double rawServing = targetCalories / food.caloriesPer100g() * 100.0;
        // 25g 단위 반올림, 최소 25g 최대 500g
        double rounded = Math.round(rawServing / 25.0) * 25.0;
        return Math.max(25.0, Math.min(500.0, rounded));
    }

    /** 이미 사용된 식품 페널티를 먼저 적용하고, 같은 후보 풀은 날짜 기준으로 안정적으로 회전한다. */
    private List<DietRecommendationCandidate> sortByScore(
            List<DietRecommendationCandidate> candidates,
            Set<Long> usedFoodIds,
            LocalDate date
    ) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingInt((DietRecommendationCandidate f) ->
                                usedFoodIds.contains(f.foodCatalogId()) ? 1 : 0)
                        .thenComparingLong(f -> rotationKey(date, f))
                        .thenComparing(Comparator.comparingLong(this::usageCount).reversed())
                        .thenComparingLong(DietRecommendationCandidate::stableKey))
                .toList();
    }

    private long usageCount(DietRecommendationCandidate food) {
        return food.usageCount();
    }

    private long rotationKey(LocalDate date, DietRecommendationCandidate food) {
        long value = date.toEpochDay() ^ (food.stableKey() * 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
