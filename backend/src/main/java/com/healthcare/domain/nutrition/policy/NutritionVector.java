package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.nutrition.dto.NutritionTargets;

public record NutritionVector(
        double calories,
        double proteinG,
        double carbsG,
        double fatG
) {
    public static NutritionVector from(NutritionTargets targets) {
        return new NutritionVector(
                targets.calorieTarget(),
                targets.proteinTargetG(),
                targets.carbTargetG(),
                targets.fatTargetG()
        );
    }
    public NutritionVector {
        requireNonNegativeFinite(calories, "calories");
        requireNonNegativeFinite(proteinG, "proteinG");
        requireNonNegativeFinite(carbsG, "carbsG");
        requireNonNegativeFinite(fatG, "fatG");
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be non-negative and finite");
        }
    }
}
