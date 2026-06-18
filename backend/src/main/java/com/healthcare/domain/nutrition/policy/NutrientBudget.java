package com.healthcare.domain.nutrition.policy;

public record NutrientBudget(
        double minimum,
        double maximum,
        boolean feasible
) {
}
