package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason;

import java.util.List;

/**
 * sweep 집계 리포트. "추천 가능 식품이 충분한가"를 숫자로 답한다.
 *
 * @param feasibleRate           feasible 시나리오 비율(0.0~1.0)
 * @param dominantFailureReason  실패 시나리오 중 최빈 사유. 모두 feasible이면 null.
 */
record FeasibilitySweepReport(
        int scenarioCount,
        int feasibleCount,
        double feasibleRate,
        RecommendationFailureReason dominantFailureReason,
        List<ScenarioFeasibility> outcomes
) {
}
