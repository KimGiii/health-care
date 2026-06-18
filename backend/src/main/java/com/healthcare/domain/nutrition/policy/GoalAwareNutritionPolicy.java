package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;

public final class GoalAwareNutritionPolicy {

    public static final String CURRENT_VERSION = "2026-06-18.v1";
    private static final double VERIFIED_CALORIE_UPPER_RATIO = 0.98;
    private static final double MUSCLE_GAIN_CALORIE_UPPER_RATIO = 1.10;
    private static final double GENERAL_CALORIE_LOWER_RATIO = 0.95;
    private static final double GENERAL_CALORIE_UPPER_RATIO = 1.05;
    private static final double GENERAL_MACRO_LOWER_RATIO = 0.90;
    private static final double GENERAL_MACRO_UPPER_RATIO = 1.10;

    public NutritionPolicy resolve(Goal.GoalType goalType, NutritionTargets targets) {
        Goal.GoalType resolvedGoalType = goalType != null ? goalType : Goal.GoalType.GENERAL_HEALTH;
        return switch (resolvedGoalType) {
            case WEIGHT_LOSS, BODY_RECOMPOSITION -> new NutritionPolicy(
                    CURRENT_VERSION,
                    resolvedGoalType,
                    NutrientConstraint.atMost(targets.calorieTarget() * VERIFIED_CALORIE_UPPER_RATIO),
                    NutrientConstraint.atLeast(targets.proteinTargetG()),
                    NutrientConstraint.unconstrained(),
                    NutrientConstraint.unconstrained()
            );
            case MUSCLE_GAIN -> new NutritionPolicy(
                    CURRENT_VERSION,
                    resolvedGoalType,
                    NutrientConstraint.between(
                            targets.calorieTarget(),
                            targets.calorieTarget() * MUSCLE_GAIN_CALORIE_UPPER_RATIO
                    ),
                    NutrientConstraint.atLeast(targets.proteinTargetG()),
                    NutrientConstraint.unconstrained(),
                    NutrientConstraint.unconstrained()
            );
            case ENDURANCE -> new NutritionPolicy(
                    CURRENT_VERSION,
                    resolvedGoalType,
                    NutrientConstraint.atLeast(targets.calorieTarget()),
                    NutrientConstraint.unconstrained(),
                    NutrientConstraint.atLeast(targets.carbTargetG()),
                    NutrientConstraint.unconstrained()
            );
            case GENERAL_HEALTH -> new NutritionPolicy(
                    CURRENT_VERSION,
                    resolvedGoalType,
                    scaledRange(targets.calorieTarget(), GENERAL_CALORIE_LOWER_RATIO, GENERAL_CALORIE_UPPER_RATIO),
                    scaledRange(targets.proteinTargetG(), GENERAL_MACRO_LOWER_RATIO, GENERAL_MACRO_UPPER_RATIO),
                    scaledRange(targets.carbTargetG(), GENERAL_MACRO_LOWER_RATIO, GENERAL_MACRO_UPPER_RATIO),
                    scaledRange(targets.fatTargetG(), GENERAL_MACRO_LOWER_RATIO, GENERAL_MACRO_UPPER_RATIO)
            );
        };
    }

    private NutrientConstraint scaledRange(double target, double lowerRatio, double upperRatio) {
        return NutrientConstraint.between(target * lowerRatio, target * upperRatio);
    }
}
