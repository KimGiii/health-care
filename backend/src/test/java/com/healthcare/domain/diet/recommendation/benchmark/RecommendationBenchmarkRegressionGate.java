package com.healthcare.domain.diet.recommendation.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * 단계적 승격용 회귀 게이트. 후보(candidate) 정책·엔진을 baseline과 같은 고정 시나리오로
 * shadow run 한 뒤, 배포 차단 안전 지표가 baseline보다 하나라도 악화되지 않았는지 검증한다.
 *
 * <p>{@link RecommendationBenchmarkGate#assertDeployable}이 "위반 절대 0"을 요구하는 최종 게이트라면,
 * 이 게이트는 baseline 대비 "악화 없음"을 요구하는 점진 승격 게이트다. 안전 지표 회귀가 없는
 * 후보만 다음 단계로 승격한다.
 */
final class RecommendationBenchmarkRegressionGate {

    private RecommendationBenchmarkRegressionGate() {
    }

    static void assertNoSafetyRegression(
            RecommendationBenchmarkReport baseline, RecommendationBenchmarkReport candidate) {
        if (baseline.scenarioCount() != candidate.scenarioCount()) {
            throw new IllegalArgumentException(
                    "scenarioCount mismatch: baseline=" + baseline.scenarioCount()
                            + ", candidate=" + candidate.scenarioCount());
        }

        List<String> regressions = new ArrayList<>();
        checkNoIncrease("blockingScenarioCount",
                baseline.blockingScenarioCount(), candidate.blockingScenarioCount(), regressions);
        checkNoIncrease("nutritionViolationCount",
                baseline.nutritionViolationCount(), candidate.nutritionViolationCount(), regressions);
        checkNoIncrease("allergenViolationScenarioCount",
                baseline.allergenViolationScenarioCount(), candidate.allergenViolationScenarioCount(), regressions);
        checkNoIncrease("incompleteDataScenarioCount",
                baseline.incompleteDataScenarioCount(), candidate.incompleteDataScenarioCount(), regressions);
        checkNoIncrease("unsupportedServingCount",
                baseline.unsupportedServingCount(), candidate.unsupportedServingCount(), regressions);
        checkNoIncrease("determinismViolationCount",
                baseline.determinismViolationCount(), candidate.determinismViolationCount(), regressions);

        if (!regressions.isEmpty()) {
            throw new AssertionError("Recommendation benchmark safety regression: " + regressions);
        }
    }

    private static void checkNoIncrease(String metric, int baseline, int candidate, List<String> regressions) {
        if (candidate > baseline) {
            regressions.add(metric + " " + baseline + "->" + candidate);
        }
    }
}
