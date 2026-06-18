package com.healthcare.domain.nutrition.policy;

public record NutrientConstraint(double minimum, double maximum) {

    public NutrientConstraint {
        if (!Double.isFinite(minimum) || minimum < 0.0) {
            throw new IllegalArgumentException("minimum must be non-negative and finite");
        }
        if (Double.isNaN(maximum) || maximum < minimum) {
            throw new IllegalArgumentException("maximum must be greater than or equal to minimum");
        }
    }

    public static NutrientConstraint atLeast(double minimum) {
        return new NutrientConstraint(minimum, Double.POSITIVE_INFINITY);
    }

    public static NutrientConstraint atMost(double maximum) {
        return new NutrientConstraint(0.0, maximum);
    }

    public static NutrientConstraint between(double minimum, double maximum) {
        return new NutrientConstraint(minimum, maximum);
    }

    public static NutrientConstraint unconstrained() {
        return atLeast(0.0);
    }

    public boolean allows(double value) {
        return value >= minimum && value <= maximum;
    }
}
