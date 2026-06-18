package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.recommendation.engine.DietRecommendationEngine;
import com.healthcare.domain.nutrition.policy.GoalAwareNutritionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("현행 greedy 추천 benchmark")
class GreedyRecommendationBaselineTest {

    private final RecommendationBenchmarkRunner runner = new RecommendationBenchmarkRunner(
            new DietRecommendationEngine(),
            new GoalAwareNutritionPolicy()
    );

    @Test
    @DisplayName("고정 시나리오에서 현행 위반과 실패를 정량 baseline으로 재현한다")
    void reproducesCurrentBaseline() {
        RecommendationBenchmarkReport report =
                RecommendationBenchmarkReport.from(runner.run(RecommendationBenchmarkFixtures.v1()));

        assertThat(report.scenarioCount()).as("전체 시나리오").isEqualTo(6);
        assertThat(report.blockingScenarioCount()).as("배포 차단 시나리오").isEqualTo(5);
        assertThat(report.nutritionViolationCount()).as("영양 hard constraint 위반").isEqualTo(8);
        assertThat(report.allergenViolationScenarioCount()).as("알러젠 검증 위반 시나리오").isEqualTo(1);
        assertThat(report.incompleteDataScenarioCount()).as("영양 데이터 결측 시나리오").isEqualTo(1);
        assertThat(report.unsupportedServingCount()).as("허용되지 않은 제공량").isEqualTo(15);
        assertThat(report.determinismViolationCount()).as("동일 조건 재현성 위반").isEqualTo(0);
    }

    @Test
    @DisplayName("미검증 알러젠 시나리오는 알러젠 검증 위반으로 차단된다")
    void unverifiedAllergenScenarioBlocksOnAllergen() {
        ScenarioOutcome outcome = runner.evaluate(scenarioById("weight-loss-unverified-allergen"));

        assertThat(outcome.allergenViolation()).isTrue();
        assertThat(outcome.blocking()).isTrue();
    }

    @Test
    @DisplayName("매크로 결측 시나리오는 영양 데이터 결측으로 차단된다")
    void incompleteMacrosScenarioBlocksOnIncompleteData() {
        ScenarioOutcome outcome = runner.evaluate(scenarioById("general-health-incomplete-macros"));

        assertThat(outcome.incompleteData()).isTrue();
        assertThat(outcome.blocking()).isTrue();
    }

    private RecommendationBenchmarkScenario scenarioById(String id) {
        return RecommendationBenchmarkFixtures.v1().stream()
                .filter(scenario -> scenario.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown scenario: " + id));
    }
}
