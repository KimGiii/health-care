package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption.ServingType;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.engine.ConstraintRecommendationEngine;
import com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.policy.GoalAwareNutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("추천 가능성 sweep")
class RecommendationFeasibilitySweepTest {

    private static final List<MealType> THREE_MEALS =
            List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER);
    private static final NutritionVector NO_INTAKE = new NutritionVector(0, 0, 0, 0);
    private static final List<Double> SERVING_STEPS_G = List.of(
            25.0, 50.0, 75.0, 100.0, 125.0, 150.0, 175.0, 200.0, 225.0, 250.0, 275.0, 300.0);

    private final RecommendationFeasibilitySweep sweep = new RecommendationFeasibilitySweep(
            new ConstraintRecommendationEngine(), new GoalAwareNutritionPolicy());

    @Test
    @DisplayName("충분한 풀·3끼·보통 목표 시나리오는 feasible로 집계된다")
    void feasibleScenarioCountsAsFeasible() {
        FeasibilitySweepScenario scenario = new FeasibilitySweepScenario(
                "weight-loss-3meals-ample",
                Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 120, 200, 60),
                THREE_MEALS,
                amplePool(),
                NO_INTAKE);

        FeasibilitySweepReport report = sweep.run(List.of(scenario));

        assertThat(report.scenarioCount()).isEqualTo(1);
        assertThat(report.feasibleCount()).isEqualTo(1);
        assertThat(report.feasibleRate()).isEqualTo(1.0);
        assertThat(report.dominantFailureReason()).isNull();
        assertThat(report.outcomes()).singleElement()
                .satisfies(o -> {
                    assertThat(o.feasible()).isTrue();
                    assertThat(o.failureReason()).isNull();
                });
    }

    @Test
    @DisplayName("저녁+간식만 선택 + 고단백 목표 + 얇은 풀은 infeasible로 집계된다 (스크린샷 재현)")
    void partialMealsHighProteinThinPoolIsInfeasible() {
        FeasibilitySweepScenario scenario = new FeasibilitySweepScenario(
                "muscle-gain-dinner-snack-thin",
                Goal.GoalType.MUSCLE_GAIN,
                new NutritionTargets(2218, 184, 232, 62),
                List.of(MealType.DINNER, MealType.SNACK),
                thinVerifiedPool(),
                NO_INTAKE);

        FeasibilitySweepReport report = sweep.run(List.of(scenario));

        assertThat(report.feasibleCount()).isZero();
        assertThat(report.outcomes()).singleElement()
                .satisfies(o -> {
                    assertThat(o.feasible()).isFalse();
                    assertThat(o.failureReason())
                            .isEqualTo(RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE);
                });
    }

    @Test
    @DisplayName("feasible·infeasible 혼합 시 feasibleRate를 집계한다")
    void aggregatesFeasibleRateAcrossScenarios() {
        FeasibilitySweepScenario feasible = new FeasibilitySweepScenario(
                "weight-loss-3meals-ample",
                Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 120, 200, 60),
                THREE_MEALS, amplePool(), NO_INTAKE);
        FeasibilitySweepScenario infeasible = new FeasibilitySweepScenario(
                "muscle-gain-dinner-snack-thin",
                Goal.GoalType.MUSCLE_GAIN,
                new NutritionTargets(2218, 184, 232, 62),
                List.of(MealType.DINNER, MealType.SNACK), thinVerifiedPool(), NO_INTAKE);

        FeasibilitySweepReport report = sweep.run(List.of(feasible, infeasible));

        assertThat(report.scenarioCount()).isEqualTo(2);
        assertThat(report.feasibleCount()).isEqualTo(1);
        assertThat(report.feasibleRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("실패 시나리오 중 최빈 사유를 dominantFailureReason으로 보고한다")
    void reportsDominantFailureReason() {
        // 같은 사유로 막히는 두 시나리오 + feasible 하나 → 최빈 사유는 REMAINING_MEALS_INFEASIBLE
        FeasibilitySweepScenario feasible = new FeasibilitySweepScenario(
                "feasible", Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 120, 200, 60),
                THREE_MEALS, amplePool(), NO_INTAKE);
        FeasibilitySweepScenario infeasibleA = new FeasibilitySweepScenario(
                "infeasible-a", Goal.GoalType.MUSCLE_GAIN,
                new NutritionTargets(2218, 184, 232, 62),
                List.of(MealType.DINNER, MealType.SNACK), thinVerifiedPool(), NO_INTAKE);
        FeasibilitySweepScenario infeasibleB = new FeasibilitySweepScenario(
                "infeasible-b", Goal.GoalType.MUSCLE_GAIN,
                new NutritionTargets(2400, 190, 240, 65),
                List.of(MealType.DINNER, MealType.SNACK), thinVerifiedPool(), NO_INTAKE);

        FeasibilitySweepReport report = sweep.run(List.of(feasible, infeasibleA, infeasibleB));

        assertThat(report.dominantFailureReason())
                .isEqualTo(RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE);
    }

    // ─── 합성 후보 풀 (ConstraintEngineBenchmarkTest 패턴 재사용) ───

    /** 검증 통과 후보가 얇은 풀(저단백·소수 단백질원) — 운영의 ~54개 verified 풀 모사. */
    private List<DietRecommendationCandidate> thinVerifiedPool() {
        return List.of(
                food(101L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165, 31, 0, 3.6),
                food(102L, "현미밥", FoodCategory.GRAIN, 130, 2.7, 28, 1),
                food(103L, "브로콜리", FoodCategory.VEGETABLE, 34, 2.8, 7, 0.4),
                food(104L, "사과", FoodCategory.FRUIT, 52, 0.3, 14, 0.2),
                food(105L, "그릭요거트", FoodCategory.DAIRY, 59, 10, 3.6, 0.4),
                food(106L, "바나나", FoodCategory.FRUIT, 89, 1.1, 23, 0.3)
        );
    }


    /** 칼로리 밀도가 다양한 검증된 제공량 후보 풀. 여러 목표를 충족할 수 있다. */
    private List<DietRecommendationCandidate> amplePool() {
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
                food(13L, "그래놀라", FoodCategory.GRAIN, 450, 10, 60, 15),
                food(14L, "치즈", FoodCategory.DAIRY, 402, 25, 1, 33),
                food(15L, "아몬드", FoodCategory.FAT, 579, 21, 22, 50),
                food(16L, "소고기", FoodCategory.PROTEIN_SOURCE, 250, 26, 0, 15)
        );
    }

    private DietRecommendationCandidate food(
            long id, String name, FoodCategory category,
            double cal, double protein, double carbs, double fat) {
        List<ServingOptionSnapshot> options = new ArrayList<>();
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
