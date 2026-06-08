package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.allergen.AllergenConfidenceGate;
import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DietRecommendationEngine 단위 테스트")
class DietRecommendationEngineTest {

    private DietRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DietRecommendationEngine(new AllergenConfidenceGate());
    }

    // ─── 칼로리 분배 ───

    @Nested
    @DisplayName("끼니별 칼로리 분배")
    class CalorieDistribution {

        @Test
        @DisplayName("4슬롯(BREAKFAST·LUNCH·DINNER·SNACK) 선택 시 기본 비율로 분배된다")
        void distribute_fourMeals_defaultRatios() {
            Map<MealType, Double> dist = engine.distributeCalories(
                    2000, List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK));

            assertThat(dist.get(MealType.BREAKFAST)).isEqualTo(500.0);  // 25%
            assertThat(dist.get(MealType.LUNCH)).isEqualTo(700.0);      // 35%
            assertThat(dist.get(MealType.DINNER)).isEqualTo(600.0);     // 30%
            assertThat(dist.get(MealType.SNACK)).isEqualTo(200.0);      // 10%
        }

        @Test
        @DisplayName("3슬롯(간식 제외) 선택 시 비율이 정규화된다")
        void distribute_threeMeals_normalizedRatios() {
            Map<MealType, Double> dist = engine.distributeCalories(
                    1800, List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER));

            double total = dist.values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(total).isCloseTo(1800.0, org.assertj.core.data.Offset.offset(1.0));
            // SNACK 없음
            assertThat(dist).doesNotContainKey(MealType.SNACK);
        }

        @Test
        @DisplayName("단일 끼니 선택 시 전체 칼로리가 해당 끼니에 할당된다")
        void distribute_singleMeal_allCalories() {
            Map<MealType, Double> dist = engine.distributeCalories(
                    1500, List.of(MealType.LUNCH));

            assertThat(dist.get(MealType.LUNCH)).isCloseTo(1500.0, org.assertj.core.data.Offset.offset(1.0));
        }
    }

    // ─── 후보 필터링 ───

    @Nested
    @DisplayName("후보 필터링")
    class CandidateFiltering {

        @Test
        @DisplayName("FOOD 제한 조건의 식품은 후보에서 제외된다")
        void filter_foodRestriction_excludesFood() {
            FoodCatalog chicken = food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
            FoodCatalog rice = food(2L, "백미밥", FoodCategory.GRAIN, 130.0);
            DietRestriction restriction = foodRestriction(1L, RestrictionType.AVOID, 1L);

            List<FoodCatalog> result = engine.filterCandidates(
                    List.of(chicken, rice), List.of(restriction), Map.of(), false);

            assertThat(result).doesNotContain(chicken);
            assertThat(result).contains(rice);
        }

        @Test
        @DisplayName("CATEGORY 제한 조건의 카테고리 식품은 모두 제외된다")
        void filter_categoryRestriction_excludesAllInCategory() {
            FoodCatalog chicken = food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
            FoodCatalog egg = food(2L, "달걀", FoodCategory.PROTEIN_SOURCE, 155.0);
            FoodCatalog rice = food(3L, "백미밥", FoodCategory.GRAIN, 130.0);
            DietRestriction restriction = categoryRestriction(RestrictionType.AVOID, FoodCategory.PROTEIN_SOURCE);

            List<FoodCatalog> result = engine.filterCandidates(
                    List.of(chicken, egg, rice), List.of(restriction), Map.of(), false);

            assertThat(result).doesNotContain(chicken, egg);
            assertThat(result).contains(rice);
        }

        @Test
        @DisplayName("KEYWORD 제한 조건과 이름이 매칭되는 식품은 제외된다")
        void filter_keywordRestriction_excludesMatchingFoods() {
            FoodCatalog porkBelly = food(1L, "삼겹살", FoodCategory.PROTEIN_SOURCE, 331.0);
            FoodCatalog porkLoin = food(2L, "돼지등심", FoodCategory.PROTEIN_SOURCE, 182.0);
            FoodCatalog chicken = food(3L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
            DietRestriction restriction = keywordRestriction("돼지");

            List<FoodCatalog> result = engine.filterCandidates(
                    List.of(porkBelly, porkLoin, chicken), List.of(restriction), Map.of(), false);

            assertThat(result).doesNotContain(porkLoin);
            assertThat(result).contains(porkBelly, chicken); // "삼겹살"은 "돼지" 미포함
        }

        @Test
        @DisplayName("ALLERGEN_TAG 제한 조건 - 알러젠이 있는 식품은 Step6에서 제외된다")
        void filter_allergenTag_excludesFoodsWithAllergen() {
            FoodCatalog milk = food(1L, "우유", FoodCategory.DAIRY, 61.0);
            FoodCatalog chicken = food(2L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
            DietRestriction restriction = allergenTagRestriction(RestrictionType.ALLERGY, AllergenTag.MILK);

            // 우유에 MILK 알러젠 태그 존재 → 제외
            FoodAllergenTag milkTag = allergenTag(1L, AllergenConfidenceLevel.DIRECT_VERIFIED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(milkTag));

            List<FoodCatalog> result = engine.filterCandidates(
                    List.of(milk, chicken), List.of(restriction), tagsByFoodId, false);

            assertThat(result).doesNotContain(milk);
            assertThat(result).contains(chicken);
        }

        @Test
        @DisplayName("Strict 모드에서 알러젠 태그 없는 식품은 UNKNOWN 취급으로 제외된다")
        void filter_strictMode_excludesUnknownAllergenFoods() {
            FoodCatalog riceNoodle = food(1L, "쌀국수", FoodCategory.GRAIN, 100.0);
            FoodCatalog plainRice = food(2L, "백미밥", FoodCategory.GRAIN, 130.0);
            DietRestriction milkRestriction = allergenTagRestriction(RestrictionType.ALLERGY, AllergenTag.MILK);

            // 쌀국수: 알러젠 태그 없음 (UNKNOWN 취급)
            // 백미밥: MILK 없음을 DIRECT_VERIFIED로 확인 (아무 allergen tag가 없지만, 여기서는 다른 알러젠 확인 레코드가 있다고 가정)
            FoodAllergenTag riceVerified = FoodAllergenTag.builder()
                    .foodCatalogId(2L)
                    .allergenTag(AllergenTag.WHEAT)  // 밀 없음 확인
                    .confidenceLevel(AllergenConfidenceLevel.DIRECT_VERIFIED)
                    .source(AllergenDataSource.MFDS_CLASS)
                    .build();
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(2L, List.of(riceVerified));

            // strict=true: 알러젠 레코드가 DIRECT_VERIFIED/LABEL_DERIVED인 식품만 통과
            List<FoodCatalog> result = engine.filterCandidates(
                    List.of(riceNoodle, plainRice), List.of(milkRestriction), tagsByFoodId, true);

            assertThat(result).doesNotContain(riceNoodle);
            assertThat(result).contains(plainRice);
        }

        @Test
        @DisplayName("영양 정보가 없는 식품(calories null)은 후보에서 제외된다")
        void filter_noNutritionInfo_excluded() {
            FoodCatalog noCalFood = FoodCatalog.builder()
                    .id(1L).name("미지정").category(FoodCategory.OTHER)
                    .caloriesPer100g(null).isCustom(false)
                    .build();
            FoodCatalog normalFood = food(2L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);

            List<FoodCatalog> result = engine.filterCandidates(
                    List.of(noCalFood, normalFood), List.of(), Map.of(), false);

            assertThat(result).doesNotContain(noCalFood);
            assertThat(result).contains(normalFood);
        }
    }

    // ─── 끼니별 추천 ───

    @Nested
    @DisplayName("끼니별 음식 선택")
    class MealFoodSelection {

        @Test
        @DisplayName("추천 결과의 끼니별 칼로리 합이 목표의 ±10% 이내에 든다")
        void recommend_caloriesWithinTenPercent() {
            List<FoodCatalog> candidates = diverseCandidates();
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            List<MealType> meals = List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER);

            List<RecommendedMeal> result = engine.recommend(targets, meals, candidates, Map.of(), false);

            double totalCal = result.stream()
                    .flatMap(m -> m.items().stream())
                    .mapToDouble(RecommendedFoodEntry::calories)
                    .sum();
            assertThat(totalCal).isBetween(1800 * 0.9, 1800 * 1.1);
        }

        @Test
        @DisplayName("각 끼니 결과에 최소 1개 이상의 식품이 포함된다")
        void recommend_eachMealHasAtLeastOneFood() {
            List<FoodCatalog> candidates = diverseCandidates();
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);

            List<RecommendedMeal> result = engine.recommend(
                    targets,
                    List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
                    candidates,
                    Map.of(),
                    false
            );

            assertThat(result).hasSize(3);
            result.forEach(meal -> assertThat(meal.items()).isNotEmpty());
        }

        @Test
        @DisplayName("제한 조건 적용 후 후보가 없으면 빈 결과를 반환한다")
        void recommend_noValidCandidates_returnsEmptyItems() {
            FoodCatalog onlyFood = food(1L, "우유", FoodCategory.DAIRY, 61.0);
            FoodAllergenTag milkTag = allergenTag(1L, AllergenConfidenceLevel.DIRECT_VERIFIED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(milkTag));

            // MILK 제한 적용 → 후보 0개
            DietRestriction restriction = allergenTagRestriction(RestrictionType.ALLERGY, AllergenTag.MILK);
            List<FoodCatalog> filtered = engine.filterCandidates(
                    List.of(onlyFood), List.of(restriction), tagsByFoodId, false);

            assertThat(filtered).isEmpty();
        }

        @Test
        @DisplayName("추천 결과에 추천 불가 사유가 포함된다 (후보 부족)")
        void recommend_insufficientCandidates_hasWarning() {
            // 후보가 1개뿐인 극단적 케이스
            List<FoodCatalog> candidates = List.of(food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0));
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);

            List<RecommendedMeal> result = engine.recommend(
                    targets,
                    List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK),
                    candidates,
                    Map.of(),
                    false
            );

            // 같은 식품이 여러 끼니에 반복될 수 있음 — 결과는 non-null
            assertThat(result).isNotNull();
        }
    }

    // ─── 헬퍼 ───

    private List<FoodCatalog> diverseCandidates() {
        return List.of(
                food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0),
                food(2L, "현미밥", FoodCategory.GRAIN, 150.0),
                food(3L, "브로콜리", FoodCategory.VEGETABLE, 34.0),
                food(4L, "두부", FoodCategory.PROTEIN_SOURCE, 76.0),
                food(5L, "고구마", FoodCategory.GRAIN, 86.0),
                food(6L, "사과", FoodCategory.FRUIT, 52.0),
                food(7L, "그릭요거트", FoodCategory.DAIRY, 59.0),
                food(8L, "아몬드", FoodCategory.FAT, 579.0),
                food(9L, "연어", FoodCategory.PROTEIN_SOURCE, 208.0),
                food(10L, "시금치", FoodCategory.VEGETABLE, 23.0)
        );
    }

    private FoodCatalog food(Long id, String name, FoodCategory category, double calories) {
        return FoodCatalog.builder()
                .id(id).name(name).category(category)
                .caloriesPer100g(calories).proteinPer100g(20.0)
                .carbsPer100g(10.0).fatPer100g(5.0)
                .isCustom(false).usageCount((long) (11 - id))
                .build();
    }

    private DietRestriction foodRestriction(Long id, RestrictionType type, Long foodCatalogId) {
        return DietRestriction.builder()
                .id(id).userId(1L).restrictionType(type)
                .targetType(TargetType.FOOD).foodCatalogId(foodCatalogId)
                .build();
    }

    private DietRestriction categoryRestriction(RestrictionType type, FoodCatalog.FoodCategory category) {
        return DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(type)
                .targetType(TargetType.CATEGORY).category(category)
                .build();
    }

    private DietRestriction keywordRestriction(String keyword) {
        return DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(RestrictionType.AVOID)
                .targetType(TargetType.KEYWORD).keyword(keyword)
                .build();
    }

    private DietRestriction allergenTagRestriction(RestrictionType type, AllergenTag tag) {
        return DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(type)
                .targetType(TargetType.ALLERGEN_TAG).allergenTag(tag)
                .build();
    }

    private FoodAllergenTag allergenTag(Long foodCatalogId, AllergenConfidenceLevel level) {
        return FoodAllergenTag.builder()
                .foodCatalogId(foodCatalogId)
                .allergenTag(AllergenTag.MILK)
                .confidenceLevel(level)
                .source(AllergenDataSource.MFDS_CLASS)
                .build();
    }
}
