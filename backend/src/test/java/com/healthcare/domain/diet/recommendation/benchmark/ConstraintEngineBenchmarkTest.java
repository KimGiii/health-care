package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption.ServingType;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.engine.ConstraintRecommendationEngine;
import com.healthcare.domain.diet.recommendation.engine.OnlinePreferencePolicy;
import com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason;
import com.healthcare.domain.diet.recommendation.engine.RecommendationResult;
import com.healthcare.domain.diet.recommendation.engine.UserRepetitionPolicy;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.policy.GoalAwareNutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionEstimate;
import com.healthcare.domain.nutrition.policy.NutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionVector;
import com.healthcare.domain.nutrition.policy.RemainingNutritionBudget;
import com.healthcare.domain.nutrition.policy.RemainingNutritionCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제약 최적화 엔진 benchmark (Phase 3). use case와 동일하게 실제 정책으로 budget을 산출해
 * 고정 시나리오를 돌린다(리뷰 P2-7: greedy용 v1과 분리한 v2).
 *
 * <p>배포 차단 안전 지표: FEASIBLE 시나리오는 budget hard constraint 충족 + 끼니 간 foodId 교집합 0
 * + 허용된 제공량만 + 재현성. 실패 시나리오는 기대한 구조화 실패 사유와 일치.
 */
@DisplayName("제약 최적화 엔진 benchmark v2")
class ConstraintEngineBenchmarkTest {

    private final ConstraintRecommendationEngine engine = new ConstraintRecommendationEngine();
    private final GoalAwareNutritionPolicy policies = new GoalAwareNutritionPolicy();
    private final RemainingNutritionCalculator calculator = new RemainingNutritionCalculator();

    private static final LocalDate DATE = LocalDate.of(2026, 6, 19);
    private static final List<MealType> THREE_MEALS =
            List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER);
    private static final NutritionVector NO_INTAKE = new NutritionVector(0, 0, 0, 0);
    // 검증된 g step 제공량(§7.1): 25~300g, 25g 간격
    private static final List<Double> SERVING_STEPS_G = List.of(
            25.0, 50.0, 75.0, 100.0, 125.0, 150.0, 175.0, 200.0, 225.0, 250.0, 275.0, 300.0);
    private static final Set<Double> ALLOWED_SERVING_G = Set.copyOf(SERVING_STEPS_G);

    @Test
    @DisplayName("체중 감량: budget 충족 + 끼니 간 중복 0 + 허용 제공량만")
    void weightLoss_feasibleAndSafe() {
        assertFeasibleAndSafe(Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 120, 200, 60), NO_INTAKE);
    }

    @Test
    @DisplayName("근육량 증가: budget 충족 + 끼니 간 중복 0 + 허용 제공량만")
    void muscleGain_feasibleAndSafe() {
        assertFeasibleAndSafe(Goal.GoalType.MUSCLE_GAIN,
                new NutritionTargets(2500, 150, 280, 70), NO_INTAKE);
    }

    @Test
    @DisplayName("지구력 향상: budget 충족 + 끼니 간 중복 0 + 허용 제공량만")
    void endurance_feasibleAndSafe() {
        assertFeasibleAndSafe(Goal.GoalType.ENDURANCE,
                new NutritionTargets(2700, 110, 260, 70), NO_INTAKE);
    }

    @Test
    @DisplayName("동일 시나리오는 재현 가능하다 (재현성 위반 0)")
    void deterministicAcrossRuns() {
        RemainingNutritionBudget budget = budgetFor(
                Goal.GoalType.WEIGHT_LOSS, new NutritionTargets(2000, 120, 200, 60), NO_INTAKE);
        RecommendationResult first = run(budget);
        RecommendationResult second = run(budget);

        assertThat(foodIds(primaryMeals(first))).isEqualTo(foodIds(primaryMeals(second)));
    }

    @Test
    @DisplayName("이미 칼로리 상한을 초과 섭취하면 CALORIE_PROTEIN_CONFLICT로 실패한다")
    void alreadyOverCalorieCap_returnsConflict() {
        RemainingNutritionBudget budget = budgetFor(
                Goal.GoalType.WEIGHT_LOSS, new NutritionTargets(2000, 120, 200, 60),
                new NutritionVector(2500, 130, 220, 70)); // 상한 초과 섭취

        RecommendationResult result = run(budget);

        assertThat(result).isInstanceOf(RecommendationResult.Failure.class);
        assertThat(((RecommendationResult.Failure) result).reason())
                .isEqualTo(RecommendationFailureReason.CALORIE_PROTEIN_CONFLICT);
    }

    @Test
    @DisplayName("도달 불가능한 단백질 하한은 구조화된 실패로 반환된다")
    void unreachableProtein_returnsStructuredFailure() {
        // 단백질 하한 400g — 현실 후보로 도달 불가
        RemainingNutritionBudget budget = budgetFor(
                Goal.GoalType.WEIGHT_LOSS, new NutritionTargets(1800, 400, 180, 50), NO_INTAKE);

        RecommendationResult result = run(budget);

        assertThat(result).isInstanceOf(RecommendationResult.Failure.class);
        assertThat(((RecommendationResult.Failure) result).reason())
                .isIn(RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE,
                        RecommendationFailureReason.SEARCH_LIMIT_REACHED);
    }

    // ─── 공통 검증 ───

    private void assertFeasibleAndSafe(
            Goal.GoalType goalType, NutritionTargets targets, NutritionVector intake) {
        RemainingNutritionBudget budget = budgetFor(goalType, targets, intake);
        RecommendationResult result = run(budget);

        assertThat(result.succeeded())
                .as("%s 시나리오는 feasible 해를 내야 한다", goalType).isTrue();
        List<RecommendedMeal> meals = primaryMeals(result);

        // 끼니 간 foodId 교집합 0 (중복 hard)
        assertThat(foodIds(meals)).doesNotHaveDuplicates();
        // 끼니별 최소 1개
        assertThat(meals).allSatisfy(meal -> assertThat(meal.items()).isNotEmpty());
        // 허용된 제공량만
        assertThat(entries(meals)).allSatisfy(item ->
                assertThat(ALLOWED_SERVING_G).contains(item.servingG()));
        // budget hard constraint 충족
        assertThat(total(meals, RecommendedMeal::totalCalories))
                .isBetween(budget.calories().minimum(), budget.calories().maximum());
        assertThat(total(meals, RecommendedMeal::totalProteinG))
                .isGreaterThanOrEqualTo(budget.protein().minimum());
        assertThat(total(meals, RecommendedMeal::totalCarbsG))
                .isGreaterThanOrEqualTo(budget.carbs().minimum());
    }

    private RecommendationResult run(RemainingNutritionBudget budget) {
        return engine.recommend(DATE, budget, THREE_MEALS, pool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(),
                3, GoalAwareNutritionPolicy.CURRENT_VERSION);
    }

    private RemainingNutritionBudget budgetFor(
            Goal.GoalType goalType, NutritionTargets targets, NutritionVector intake) {
        NutritionPolicy policy = policies.resolve(goalType, targets);
        return calculator.calculate(policy, List.of(NutritionEstimate.confirmed(intake)));
    }

    private List<RecommendedMeal> primaryMeals(RecommendationResult result) {
        return ((RecommendationResult.Success) result).primary().meals();
    }

    private List<RecommendedFoodEntry> entries(List<RecommendedMeal> meals) {
        return meals.stream().flatMap(m -> m.items().stream()).toList();
    }

    private List<Long> foodIds(List<RecommendedMeal> meals) {
        return entries(meals).stream().map(RecommendedFoodEntry::foodCatalogId).toList();
    }

    private double total(List<RecommendedMeal> meals, java.util.function.ToDoubleFunction<RecommendedMeal> f) {
        return meals.stream().mapToDouble(f).sum();
    }

    /** 검증된 g step 제공량을 가진 풍부한 후보 풀. 칼로리 밀도가 다양해 여러 목표를 충족할 수 있다. */
    private List<DietRecommendationCandidate> pool() {
        return List.of(
                food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165, 31, 0, 3.6),
                food(2L, "현미밥", FoodCategory.GRAIN, 130, 2.7, 28, 1),
                food(3L, "브로콜리", FoodCategory.VEGETABLE, 34, 2.8, 7, 0.4),
                food(4L, "사과", FoodCategory.FRUIT, 52, 0.3, 14, 0.2),
                food(5L, "두부", FoodCategory.PROTEIN_SOURCE, 76, 8, 2, 4.8),
                food(6L, "고구마", FoodCategory.GRAIN, 86, 1.6, 20, 0.1),
                food(7L, "시금치", FoodCategory.VEGETABLE, 23, 2.9, 3.6, 0.4),
                food(8L, "바나나", FoodCategory.FRUIT, 89, 1.1, 23, 0.3),
                food(9L, "연어", FoodCategory.PROTEIN_SOURCE, 208, 20, 0, 13),
                food(10L, "오트밀", FoodCategory.GRAIN, 68, 2.4, 12, 1.4),
                food(11L, "달걀", FoodCategory.PROTEIN_SOURCE, 155, 13, 1.1, 11),
                food(12L, "그릭요거트", FoodCategory.DAIRY, 59, 10, 3.6, 0.4),
                // 칼로리 밀도가 높은 staple — 고칼로리 목표 충족용
                food(13L, "그래놀라", FoodCategory.GRAIN, 450, 10, 60, 15),
                food(14L, "치즈", FoodCategory.DAIRY, 402, 25, 1, 33),
                food(15L, "아몬드", FoodCategory.FAT, 579, 21, 22, 50),
                food(16L, "소고기", FoodCategory.PROTEIN_SOURCE, 250, 26, 0, 15)
        );
    }

    private DietRecommendationCandidate food(
            long id, String name, FoodCategory category,
            double cal, double protein, double carbs, double fat) {
        List<ServingOptionSnapshot> options = new java.util.ArrayList<>();
        for (int i = 0; i < SERVING_STEPS_G.size(); i++) {
            double g = SERVING_STEPS_G.get(i);
            options.add(serving((int) g + "g", g, i));
        }
        return new DietRecommendationCandidate(
                id, name, name, category, cal, protein, carbs, fat, 0L, id,
                AllergenConfidenceLevel.DIRECT_VERIFIED, null, true, options, true);
    }

    private ServingOptionSnapshot serving(String label, double g, int sortOrder) {
        return new ServingOptionSnapshot(label, label, g, sortOrder, ServingType.OFFICIAL_SERVING, true);
    }
}
