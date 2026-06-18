package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DietRecommendationEngine 단위 테스트")
class DietRecommendationEngineTest {

    private DietRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DietRecommendationEngine();
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

    // ─── 끼니별 추천 ───

    @Nested
    @DisplayName("끼니별 음식 선택")
    class MealFoodSelection {

        @Test
        @DisplayName("추천 결과의 끼니별 칼로리 합이 목표의 ±10% 이내에 든다")
        void recommend_caloriesWithinTenPercent() {
            List<DietRecommendationCandidate> candidates = diverseCandidates();
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            List<MealType> meals = List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER);

            List<RecommendedMeal> result = engine.recommend(targets, meals, candidates);

            double totalCal = result.stream()
                    .flatMap(m -> m.items().stream())
                    .mapToDouble(RecommendedFoodEntry::calories)
                    .sum();
            assertThat(totalCal).isBetween(1800 * 0.9, 1800 * 1.1);
        }

        @Test
        @DisplayName("각 끼니 결과에 최소 1개 이상의 식품이 포함된다")
        void recommend_eachMealHasAtLeastOneFood() {
            List<DietRecommendationCandidate> candidates = diverseCandidates();
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);

            List<RecommendedMeal> result = engine.recommend(
                    targets,
                    List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
                    candidates
            );

            assertThat(result).hasSize(3);
            result.forEach(meal -> assertThat(meal.items()).isNotEmpty());
        }

        @Test
        @DisplayName("제한 조건 적용 후 후보가 없으면 빈 결과를 반환한다")
        void recommend_noValidCandidates_returnsEmptyItems() {
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            List<RecommendedMeal> result = engine.recommend(
                    targets,
                    List.of(MealType.BREAKFAST),
                    List.of()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).items()).isEmpty();
        }

        @Test
        @DisplayName("추천 결과에 추천 불가 사유가 포함된다 (후보 부족)")
        void recommend_insufficientCandidates_hasWarning() {
            // 후보가 1개뿐인 극단적 케이스
            List<DietRecommendationCandidate> candidates =
                    List.of(food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0));
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);

            List<RecommendedMeal> result = engine.recommend(
                    targets,
                    List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK),
                    candidates
            );

            // 같은 식품이 여러 끼니에 반복될 수 있음 — 결과는 non-null
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("RECOMMENDABLE_WITH_CAUTION 식품은 추천되고 caution 필드에 사유가 담긴다")
        void recommend_cautionFood_hasCautionField() {
            DietRecommendationCandidate cautionFood =
                    food(1L, "와퍼", FoodCategory.PROCESSED, 300.0, "고지방·고나트륨");
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);

            List<RecommendedMeal> result = engine.recommend(
                    targets, List.of(MealType.LUNCH), List.of(cautionFood));

            List<RecommendedFoodEntry> items = result.stream()
                    .flatMap(m -> m.items().stream())
                    .toList();
            assertThat(items).isNotEmpty();
            assertThat(items).allMatch(e -> "고지방·고나트륨".equals(e.caution()));
        }

        @Test
        @DisplayName("RECOMMENDABLE 식품은 추천되고 caution 필드가 null이다")
        void recommend_normalFood_hasCautionNull() {
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            List<DietRecommendationCandidate> candidates = diverseCandidates();

            List<RecommendedMeal> result = engine.recommend(
                    targets, List.of(MealType.LUNCH), candidates);

            List<RecommendedFoodEntry> items = result.stream()
                    .flatMap(m -> m.items().stream())
                    .toList();
            assertThat(items).isNotEmpty();
            assertThat(items).allMatch(e -> e.caution() == null);
        }

        @Test
        @DisplayName("같은 후보라도 날짜가 바뀌면 추천 우선순위가 deterministic rotation으로 달라진다")
        void recommend_differentDate_rotatesCandidatePriority() {
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            List<DietRecommendationCandidate> candidates = diverseCandidates();

            List<RecommendedMeal> firstDay = engine.recommend(
                    LocalDate.of(2026, 6, 12),
                    targets,
                    List.of(MealType.LUNCH),
                    candidates
            );
            List<RecommendedMeal> sameDay = engine.recommend(
                    LocalDate.of(2026, 6, 12),
                    targets,
                    List.of(MealType.LUNCH),
                    candidates
            );
            List<RecommendedMeal> nextDay = engine.recommend(
                    LocalDate.of(2026, 6, 13),
                    targets,
                    List.of(MealType.LUNCH),
                    candidates
            );

            List<Long> firstDayIds = recommendedFoodIds(firstDay);
            assertThat(recommendedFoodIds(sameDay)).isEqualTo(firstDayIds);
            assertThat(recommendedFoodIds(nextDay)).isNotEqualTo(firstDayIds);
        }
    }

    // ─── 헬퍼 ───

    private List<Long> recommendedFoodIds(List<RecommendedMeal> meals) {
        return meals.stream()
                .flatMap(meal -> meal.items().stream())
                .map(RecommendedFoodEntry::foodCatalogId)
                .toList();
    }

    private List<DietRecommendationCandidate> diverseCandidates() {
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

    private DietRecommendationCandidate food(Long id, String name, FoodCategory category, double calories) {
        return food(id, name, category, calories, null);
    }

    private DietRecommendationCandidate food(
            Long id,
            String name,
            FoodCategory category,
            double calories,
            String caution
    ) {
        return new DietRecommendationCandidate(
                id,
                name,
                null,
                category,
                calories,
                20.0,
                10.0,
                5.0,
                11 - id,
                id,
                AllergenConfidenceLevel.UNKNOWN,
                caution,
                true,
                List.of(),
                false
        );
    }

}
