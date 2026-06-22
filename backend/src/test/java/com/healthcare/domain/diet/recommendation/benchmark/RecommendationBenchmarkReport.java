package com.healthcare.domain.diet.recommendation.benchmark;

import java.util.List;

record RecommendationBenchmarkReport(
        int scenarioCount,
        int blockingScenarioCount,
        int nutritionViolationCount,
        int allergenViolationScenarioCount,
        int incompleteDataScenarioCount,
        int unsupportedServingCount,
        int determinismViolationCount
) {
    static RecommendationBenchmarkReport from(List<ScenarioOutcome> outcomes) {
        return new RecommendationBenchmarkReport(
                outcomes.size(),
                (int) outcomes.stream().filter(ScenarioOutcome::blocking).count(),
                outcomes.stream().mapToInt(ScenarioOutcome::nutritionViolationCount).sum(),
                (int) outcomes.stream().filter(ScenarioOutcome::allergenViolation).count(),
                (int) outcomes.stream().filter(ScenarioOutcome::incompleteData).count(),
                outcomes.stream().mapToInt(ScenarioOutcome::unsupportedServingCount).sum(),
                (int) outcomes.stream().filter(ScenarioOutcome::determinismViolation).count()
        );
    }
}
