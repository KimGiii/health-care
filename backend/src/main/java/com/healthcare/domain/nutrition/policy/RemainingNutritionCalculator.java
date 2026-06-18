package com.healthcare.domain.nutrition.policy;

import java.util.List;
import java.util.function.ToDoubleFunction;

public final class RemainingNutritionCalculator {

    public RemainingNutritionBudget calculate(
            NutritionPolicy policy,
            List<NutritionEstimate> intake
    ) {
        return new RemainingNutritionBudget(
                remaining(policy.calories(), intake, NutritionVector::calories),
                remaining(policy.protein(), intake, NutritionVector::proteinG),
                remaining(policy.carbs(), intake, NutritionVector::carbsG),
                remaining(policy.fat(), intake, NutritionVector::fatG)
        );
    }

    private NutrientBudget remaining(
            NutrientConstraint constraint,
            List<NutritionEstimate> intake,
            ToDoubleFunction<NutritionVector> nutrient
    ) {
        double consumedLower = intake.stream()
                .map(NutritionEstimate::lowerBound)
                .mapToDouble(nutrient)
                .sum();
        double consumedUpper = intake.stream()
                .map(NutritionEstimate::upperBound)
                .mapToDouble(nutrient)
                .sum();

        double minimum = Math.max(0.0, constraint.minimum() - consumedLower);
        double maximum = Double.isInfinite(constraint.maximum())
                ? Double.POSITIVE_INFINITY
                : Math.max(0.0, constraint.maximum() - consumedUpper);
        boolean feasible = consumedUpper <= constraint.maximum() && minimum <= maximum;
        return new NutrientBudget(minimum, maximum, feasible);
    }
}
