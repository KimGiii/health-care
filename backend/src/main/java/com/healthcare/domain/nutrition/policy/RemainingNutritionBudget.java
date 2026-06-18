package com.healthcare.domain.nutrition.policy;

public record RemainingNutritionBudget(
        NutrientBudget calories,
        NutrientBudget protein,
        NutrientBudget carbs,
        NutrientBudget fat
) {
    public boolean feasible() {
        return calories.feasible()
                && protein.feasible()
                && carbs.feasible()
                && fat.feasible();
    }
}
