package com.healthcare.domain.diet.recommendation.benchmark;

final class RecommendationBenchmarkGate {

    private RecommendationBenchmarkGate() {
    }

    static void assertDeployable(RecommendationBenchmarkReport report) {
        if (report.blockingScenarioCount() > 0
                || report.nutritionViolationCount() > 0
                || report.allergenViolationScenarioCount() > 0
                || report.incompleteDataScenarioCount() > 0
                || report.unsupportedServingCount() > 0
                || report.determinismViolationCount() > 0) {
            throw new AssertionError("Recommendation benchmark blocked deployment: " + report);
        }
    }
}
