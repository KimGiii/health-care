package com.healthcare.domain.diet.recommendation.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("추천 benchmark 배포 게이트")
class RecommendationBenchmarkGateTest {

    @Test
    @DisplayName("hard constraint·알러젠·결측·제공량·재현성 위반이 하나라도 있으면 차단한다")
    void blocksAnyDeploymentViolation() {
        RecommendationBenchmarkReport report = new RecommendationBenchmarkReport(
                1, 1, 1, 0, 0, 0, 0
        );

        assertThatThrownBy(() -> RecommendationBenchmarkGate.assertDeployable(report))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("nutritionViolationCount=1");
    }

    @Test
    @DisplayName("모든 배포 차단 지표가 0이면 통과한다")
    void allowsZeroViolationReport() {
        RecommendationBenchmarkReport report = new RecommendationBenchmarkReport(
                6, 0, 0, 0, 0, 0, 0
        );

        assertThatCode(() -> RecommendationBenchmarkGate.assertDeployable(report))
                .doesNotThrowAnyException();
    }
}
