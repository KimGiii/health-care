package com.healthcare.domain.diet.recommendation.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("추천 benchmark 회귀 게이트 (shadow run 단계적 승격)")
class RecommendationBenchmarkRegressionGateTest {

    // scenarioCount, blocking, nutrition, allergen, incompleteData, unsupportedServing, determinism
    private static final RecommendationBenchmarkReport BASELINE =
            new RecommendationBenchmarkReport(6, 5, 8, 1, 1, 15, 0);

    @Test
    @DisplayName("candidate가 baseline과 동일하면 회귀가 아니다")
    void noRegressionWhenEqual() {
        assertThatCode(() -> RecommendationBenchmarkRegressionGate.assertNoSafetyRegression(BASELINE, BASELINE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("candidate가 모든 안전 지표를 개선하면 통과한다")
    void allowsImprovement() {
        RecommendationBenchmarkReport candidate =
                new RecommendationBenchmarkReport(6, 0, 0, 0, 0, 0, 0);

        assertThatCode(() -> RecommendationBenchmarkRegressionGate.assertNoSafetyRegression(BASELINE, candidate))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("어떤 안전 지표라도 악화되면 회귀로 차단하고 해당 지표를 보고한다")
    void blocksWhenAnyMetricWorsens() {
        RecommendationBenchmarkReport candidate =
                new RecommendationBenchmarkReport(6, 5, 9, 1, 1, 15, 0); // nutrition 8 -> 9

        assertThatThrownBy(() -> RecommendationBenchmarkRegressionGate.assertNoSafetyRegression(BASELINE, candidate))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nutritionViolationCount")
                .hasMessageContaining("8->9");
    }

    @Test
    @DisplayName("일부 지표 개선과 다른 지표 악화가 섞이면 악화 지표 때문에 차단한다")
    void blocksWhenMixedImprovementAndRegression() {
        RecommendationBenchmarkReport candidate =
                new RecommendationBenchmarkReport(6, 0, 0, 0, 0, 16, 0); // serving 15 -> 16

        assertThatThrownBy(() -> RecommendationBenchmarkRegressionGate.assertNoSafetyRegression(BASELINE, candidate))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unsupportedServingCount");
    }

    @Test
    @DisplayName("시나리오 수가 다르면 비교가 무효이므로 예외를 던진다")
    void rejectsMismatchedScenarioCount() {
        RecommendationBenchmarkReport candidate =
                new RecommendationBenchmarkReport(5, 0, 0, 0, 0, 0, 0);

        assertThatThrownBy(() -> RecommendationBenchmarkRegressionGate.assertNoSafetyRegression(BASELINE, candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scenarioCount");
    }
}
