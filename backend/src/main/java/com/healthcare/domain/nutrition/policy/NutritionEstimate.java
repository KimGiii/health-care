package com.healthcare.domain.nutrition.policy;

public record NutritionEstimate(
        NutritionVector lowerBound,
        NutritionVector upperBound
) {
    public NutritionEstimate {
        requireOrdered(lowerBound.calories(), upperBound.calories(), "calories");
        requireOrdered(lowerBound.proteinG(), upperBound.proteinG(), "proteinG");
        requireOrdered(lowerBound.carbsG(), upperBound.carbsG(), "carbsG");
        requireOrdered(lowerBound.fatG(), upperBound.fatG(), "fatG");
    }

    public static NutritionEstimate confirmed(NutritionVector nutrition) {
        return new NutritionEstimate(nutrition, nutrition);
    }

    private static void requireOrdered(double lower, double upper, String name) {
        if (lower > upper) {
            throw new IllegalArgumentException(name + " lower bound must not exceed upper bound");
        }
    }
}
