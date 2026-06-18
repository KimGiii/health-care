package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.engine.DietRecommendationEngine;
import com.healthcare.domain.nutrition.policy.GoalAwareNutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionVector;

import java.util.List;

final class RecommendationBenchmarkRunner {

    private final DietRecommendationEngine engine;
    private final GoalAwareNutritionPolicy policies;

    RecommendationBenchmarkRunner(
            DietRecommendationEngine engine,
            GoalAwareNutritionPolicy policies
    ) {
        this.engine = engine;
        this.policies = policies;
    }

    List<ScenarioOutcome> run(List<RecommendationBenchmarkScenario> scenarios) {
        return scenarios.stream()
                .map(this::evaluate)
                .toList();
    }

    ScenarioOutcome evaluate(RecommendationBenchmarkScenario scenario) {
        List<RecommendedMeal> first = recommend(scenario);
        List<RecommendedMeal> second = recommend(scenario);
        NutritionPolicy policy = policies.resolve(scenario.goalType(), scenario.targets());

        return new ScenarioOutcome(
                scenario.id(),
                policy.violations(totalNutrition(first, scenario.confirmedIntake())).size(),
                hasAllergenViolation(scenario, first),
                hasIncompleteData(scenario),
                unsupportedServingCount(scenario, first),
                !first.equals(second)
        );
    }

    private List<RecommendedMeal> recommend(RecommendationBenchmarkScenario scenario) {
        return engine.recommend(
                scenario.date(),
                scenario.targets(),
                scenario.mealTypes(),
                scenario.candidates()
        );
    }

    private NutritionVector totalNutrition(List<RecommendedMeal> meals, NutritionVector consumed) {
        return new NutritionVector(
                consumed.calories() + meals.stream().mapToDouble(RecommendedMeal::totalCalories).sum(),
                consumed.proteinG() + meals.stream().mapToDouble(RecommendedMeal::totalProteinG).sum(),
                consumed.carbsG() + meals.stream().mapToDouble(RecommendedMeal::totalCarbsG).sum(),
                consumed.fatG() + meals.stream().mapToDouble(RecommendedMeal::totalFatG).sum()
        );
    }

    private boolean hasAllergenViolation(
            RecommendationBenchmarkScenario scenario,
            List<RecommendedMeal> meals
    ) {
        if (!scenario.allergyRestricted()) {
            return false;
        }
        return entries(meals).stream()
                .map(RecommendedFoodEntry::allergenConfidenceLevel)
                .anyMatch(confidence -> confidence != AllergenConfidenceLevel.DIRECT_VERIFIED
                        && confidence != AllergenConfidenceLevel.LABEL_DERIVED);
    }

    private int unsupportedServingCount(
            RecommendationBenchmarkScenario scenario,
            List<RecommendedMeal> meals
    ) {
        return (int) entries(meals).stream()
                .filter(entry -> !scenario.allowedServingG().contains(entry.servingG()))
                .count();
    }

    private boolean hasIncompleteData(RecommendationBenchmarkScenario scenario) {
        return scenario.candidates().stream().anyMatch(c -> !c.macroDataComplete());
    }

    private List<RecommendedFoodEntry> entries(List<RecommendedMeal> meals) {
        return meals.stream()
                .flatMap(meal -> meal.items().stream())
                .toList();
    }
}
