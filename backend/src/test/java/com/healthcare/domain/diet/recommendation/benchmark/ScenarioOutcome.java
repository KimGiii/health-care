package com.healthcare.domain.diet.recommendation.benchmark;

/**
 * 고정 benchmark 시나리오 1건의 측정 결과. 위반 종류를 이름으로 표현하며,
 * 종합 지표는 {@link RecommendationBenchmarkReport#from}이 outcome 목록을 fold 해서 만든다.
 */
record ScenarioOutcome(
        String scenarioId,
        int nutritionViolationCount,
        boolean allergenViolation,
        boolean incompleteData,
        int unsupportedServingCount,
        boolean determinismViolation
) {
    boolean blocking() {
        return nutritionViolationCount > 0
                || allergenViolation
                || incompleteData
                || unsupportedServingCount > 0
                || determinismViolation;
    }
}
