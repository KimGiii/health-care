package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.recommendation.engine.ConstraintRecommendationEngine;
import com.healthcare.domain.diet.recommendation.engine.OnlinePreferencePolicy;
import com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason;
import com.healthcare.domain.diet.recommendation.engine.RecommendationResult;
import com.healthcare.domain.diet.recommendation.engine.UserRepetitionPolicy;
import com.healthcare.domain.nutrition.policy.GoalAwareNutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionEstimate;
import com.healthcare.domain.nutrition.policy.NutritionPolicy;
import com.healthcare.domain.nutrition.policy.RemainingNutritionBudget;
import com.healthcare.domain.nutrition.policy.RemainingNutritionCalculator;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 추천 가능성 sweep 하니스. 시나리오 매트릭스를 엔진에 통과시켜 feasible 비율과 지배적 실패 사유를
 * 집계한다. "추천 가능 식품이 N개뿐인데 추천 로직이 제대로 검증되는가"라는 운영 커버리지 질문에
 * 합성/운영 풀로 정량 답을 준다.
 *
 * <p>use case와 동일하게 실제 정책으로 budget을 산출하고 {@link ConstraintRecommendationEngine}을
 * 직접 호출해 {@link RecommendationFailureReason}을 그대로 얻는다. 결정성 확보를 위해 고정 날짜·
 * 반복 무제약·온라인 신호 없음으로 돌린다.
 */
final class RecommendationFeasibilitySweep {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 6, 30);

    private final ConstraintRecommendationEngine engine;
    private final GoalAwareNutritionPolicy policies;
    private final RemainingNutritionCalculator calculator = new RemainingNutritionCalculator();

    RecommendationFeasibilitySweep(
            ConstraintRecommendationEngine engine,
            GoalAwareNutritionPolicy policies
    ) {
        this.engine = engine;
        this.policies = policies;
    }

    FeasibilitySweepReport run(List<FeasibilitySweepScenario> scenarios) {
        List<ScenarioFeasibility> outcomes = scenarios.stream()
                .map(this::evaluate)
                .toList();
        int feasibleCount = (int) outcomes.stream().filter(ScenarioFeasibility::feasible).count();
        double feasibleRate = outcomes.isEmpty() ? 0.0 : (double) feasibleCount / outcomes.size();
        return new FeasibilitySweepReport(
                outcomes.size(), feasibleCount, feasibleRate,
                dominantFailureReason(outcomes), outcomes);
    }

    /** 실패 시나리오 중 최빈 사유. 모두 feasible이면 null. 동률은 enum 선언 순서로 결정(결정성). */
    private RecommendationFailureReason dominantFailureReason(List<ScenarioFeasibility> outcomes) {
        Map<RecommendationFailureReason, Long> counts = outcomes.stream()
                .filter(o -> !o.feasible())
                .collect(Collectors.groupingBy(
                        ScenarioFeasibility::failureReason, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<RecommendationFailureReason, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(e -> e.getKey().ordinal(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private ScenarioFeasibility evaluate(FeasibilitySweepScenario scenario) {
        NutritionPolicy policy = policies.resolve(scenario.goalType(), scenario.targets());
        RemainingNutritionBudget budget = calculator.calculate(
                policy, List.of(NutritionEstimate.confirmed(scenario.confirmedIntake())));

        RecommendationResult result = engine.recommend(
                FIXED_DATE, budget, scenario.mealTypes(), scenario.pool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(),
                1, GoalAwareNutritionPolicy.CURRENT_VERSION);

        if (result.succeeded()) {
            return new ScenarioFeasibility(scenario.id(), true, null);
        }
        return new ScenarioFeasibility(
                scenario.id(), false, ((RecommendationResult.Failure) result).reason());
    }
}
