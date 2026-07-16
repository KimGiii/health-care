package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason;

/**
 * 한 sweep 시나리오의 결과. feasible이면 failureReason은 null이다.
 */
record ScenarioFeasibility(
        String id,
        boolean feasible,
        RecommendationFailureReason failureReason
) {
}
