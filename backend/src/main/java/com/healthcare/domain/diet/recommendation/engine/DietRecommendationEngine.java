package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

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
     * 제한 조건과 알러젠 태그를 적용해 추천 후보를 필터링한다.
     *
     * @param candidates      전체 식품 카탈로그 후보
     * @param restrictions    사용자 제한 조건 목록
     * @param tagsByFoodId    식품 ID → 알러젠 태그 목록
     * @param strictMode      Strict 모드 여부
     */
    public List<FoodCatalog> filterCandidates(
            List<FoodCatalog> candidates,
            List<DietRestriction> restrictions,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId,
            boolean strictMode
    ) {
        Set<Long> restrictedFoodIds = restrictions.stream()
                .filter(r -> r.getTargetType() == TargetType.FOOD)
                .map(DietRestriction::getFoodCatalogId)
                .collect(Collectors.toSet());

        Set<FoodCategory> restrictedCategories = restrictions.stream()
                .filter(r -> r.getTargetType() == TargetType.CATEGORY)
                .map(DietRestriction::getCategory)
                .collect(Collectors.toSet());

        List<String> restrictedKeywords = restrictions.stream()
                .filter(r -> r.getTargetType() == TargetType.KEYWORD)
                .map(DietRestriction::getKeyword)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .toList();

        Set<AllergenTag> restrictedAllergenTags = restrictions.stream()
                .filter(r -> r.getTargetType() == TargetType.ALLERGEN_TAG)
                .map(DietRestriction::getAllergenTag)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return candidates.stream()
                .filter(food -> food.getCaloriesPer100g() != null)              // 영양 정보 필수
                .filter(food -> !restrictedFoodIds.contains(food.getId()))       // FOOD 제한
                .filter(food -> !restrictedCategories.contains(food.getCategory())) // CATEGORY 제한
                .filter(food -> !matchesKeyword(food, restrictedKeywords))       // KEYWORD 제한
                .filter(food -> !containsRestrictedAllergen(food.getId(), restrictedAllergenTags, tagsByFoodId)) // 알러젠 포함 제외
                .filter(food -> passesAllergenConfidenceGate(food.getId(), restrictedAllergenTags, tagsByFoodId, strictMode)) // 신뢰 레벨 게이트
                .toList();
    }

    /**
     * 전체 하루 식단 추천을 생성한다.
     */
    public List<RecommendedMeal> recommend(
            NutritionTargets targets,
            List<MealType> selectedMeals,
            List<FoodCatalog> filteredCandidates,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId,
            boolean strictMode
    ) {
        Map<MealType, Double> calorieDistribution = distributeCalories(targets.calorieTarget(), selectedMeals);
        Set<Long> usedFoodIds = new HashSet<>();

        List<RecommendedMeal> meals = new ArrayList<>();
        for (MealType mealType : selectedMeals) {
            double targetCal = calorieDistribution.getOrDefault(mealType, 0.0);
            double targetProtein = targets.proteinTargetG() * (targetCal / targets.calorieTarget());

            List<FoodCatalog> mealCandidates = sortByScore(filteredCandidates, usedFoodIds);
            List<RecommendedFoodEntry> items = selectItemsForMeal(
                    mealType, mealCandidates, targetCal, tagsByFoodId);

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

    // ─── 내부 로직 ───

    private List<RecommendedFoodEntry> selectItemsForMeal(
            MealType mealType,
            List<FoodCatalog> candidates,
            double targetCalories,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId
    ) {
        if (candidates.isEmpty()) return List.of();

        int maxItems = mealType == MealType.SNACK ? MAX_ITEMS_PER_SNACK : MAX_ITEMS_PER_MAIN_MEAL;
        List<FoodCategory> preferredCategories = PREFERRED_CATEGORIES.getOrDefault(mealType, List.of());

        // 선호 카테고리별로 1개씩 선택 (있는 경우)
        List<FoodCatalog> selected = new ArrayList<>();
        Set<FoodCategory> usedCategories = new HashSet<>();

        for (FoodCategory preferredCat : preferredCategories) {
            if (selected.size() >= maxItems) break;
            candidates.stream()
                    .filter(f -> f.getCategory() == preferredCat && !usedCategories.contains(f.getCategory()))
                    .findFirst()
                    .ifPresent(f -> {
                        selected.add(f);
                        usedCategories.add(f.getCategory());
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
        for (FoodCatalog food : selected) {
            double servingG = calculateServing(food, perItemCalories);
            double factor = servingG / 100.0;

            AllergenConfidenceLevel confidence = resolveConfidence(food.getId(), tagsByFoodId);
            items.add(new RecommendedFoodEntry(
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
            ));
        }
        return items;
    }

    /** 목표 칼로리에 맞는 제공량(g)을 계산한다. 25g 단위로 반올림. */
    private double calculateServing(FoodCatalog food, double targetCalories) {
        if (food.getCaloriesPer100g() <= 0) return 100.0;
        double rawServing = targetCalories / food.getCaloriesPer100g() * 100.0;
        // 25g 단위 반올림, 최소 25g 최대 500g
        double rounded = Math.round(rawServing / 25.0) * 25.0;
        return Math.max(25.0, Math.min(500.0, rounded));
    }

    /** 사용 빈도(usageCount) 내림차순 + 이미 사용된 식품 페널티 정렬 */
    private List<FoodCatalog> sortByScore(List<FoodCatalog> candidates, Set<Long> usedFoodIds) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingLong((FoodCatalog f) -> usedFoodIds.contains(f.getId()) ? 0L : 1L)
                        .reversed()
                        .thenComparingLong(f -> f.getUsageCount() != null ? f.getUsageCount() : 0L)
                        .reversed())
                .toList();
    }

    /** KEYWORD 제한: 식품명 또는 한국어명에 키워드가 포함되면 제외 */
    private boolean matchesKeyword(FoodCatalog food, List<String> keywords) {
        if (keywords.isEmpty()) return false;
        String nameLower = food.getName() != null ? food.getName().toLowerCase() : "";
        String nameKoLower = food.getNameKo() != null ? food.getNameKo().toLowerCase() : "";
        return keywords.stream().anyMatch(kw -> nameLower.contains(kw) || nameKoLower.contains(kw));
    }

    /** Step 6: 제한된 알러젠이 식품에 존재하면 true (→ 제외) */
    private boolean containsRestrictedAllergen(
            Long foodId,
            Set<AllergenTag> restrictedTags,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId
    ) {
        if (restrictedTags.isEmpty()) return false;
        List<FoodAllergenTag> tags = tagsByFoodId.getOrDefault(foodId, List.of());
        return tags.stream().anyMatch(t -> restrictedTags.contains(t.getAllergenTag()));
    }

    /**
     * Step 7: 알러젠 신뢰 레벨 게이트.
     * 제한 알러젠이 없는 경우: 기본 모드 = 항상 통과 (UNKNOWN은 낮은 우선순위로 풀에 유지).
     *   strict 모드 = 알러젠 레코드가 DIRECT_VERIFIED 또는 LABEL_DERIVED인 식품만 통과.
     */
    private boolean passesAllergenConfidenceGate(
            Long foodId,
            Set<AllergenTag> restrictedAllergenTags,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId,
            boolean strictMode
    ) {
        if (restrictedAllergenTags.isEmpty()) return true;
        if (!strictMode) return true;

        // Strict 모드: 이 식품에 DIRECT_VERIFIED 또는 LABEL_DERIVED 레코드가 최소 하나 있어야 통과
        List<FoodAllergenTag> tags = tagsByFoodId.getOrDefault(foodId, List.of());
        return tags.stream().anyMatch(t ->
                t.getConfidenceLevel() == AllergenConfidenceLevel.DIRECT_VERIFIED ||
                t.getConfidenceLevel() == AllergenConfidenceLevel.LABEL_DERIVED);
    }

    /** 식품의 알러젠 검토 신뢰 레벨을 결정한다 (응답 표시용). */
    private AllergenConfidenceLevel resolveConfidence(Long foodId, Map<Long, List<FoodAllergenTag>> tagsByFoodId) {
        List<FoodAllergenTag> tags = tagsByFoodId.getOrDefault(foodId, List.of());
        if (tags.isEmpty()) return AllergenConfidenceLevel.UNKNOWN;
        return tags.stream()
                .map(FoodAllergenTag::getConfidenceLevel)
                .max(Comparator.comparingInt(AllergenConfidenceLevel::ordinal))
                .orElse(AllergenConfidenceLevel.UNKNOWN);
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
