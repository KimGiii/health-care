package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.goals.entity.Goal;

import java.util.ArrayList;
import java.util.List;

public record NutritionPolicy(
        String version,
        Goal.GoalType goalType,
        NutrientConstraint calories,
        NutrientConstraint protein,
        NutrientConstraint carbs,
        NutrientConstraint fat
) {
    public boolean accepts(NutritionVector nutrition) {
        return violations(nutrition).isEmpty();
    }

    public List<NutritionPolicyViolation> violations(NutritionVector nutrition) {
        ArrayList<NutritionPolicyViolation> violations = new ArrayList<>();
        addViolations(violations, calories, nutrition.calories(),
                NutritionPolicyViolation.CALORIES_BELOW_MINIMUM,
                NutritionPolicyViolation.CALORIES_ABOVE_MAXIMUM);
        addViolations(violations, protein, nutrition.proteinG(),
                NutritionPolicyViolation.PROTEIN_BELOW_MINIMUM,
                NutritionPolicyViolation.PROTEIN_ABOVE_MAXIMUM);
        addViolations(violations, carbs, nutrition.carbsG(),
                NutritionPolicyViolation.CARBS_BELOW_MINIMUM,
                NutritionPolicyViolation.CARBS_ABOVE_MAXIMUM);
        addViolations(violations, fat, nutrition.fatG(),
                NutritionPolicyViolation.FAT_BELOW_MINIMUM,
                NutritionPolicyViolation.FAT_ABOVE_MAXIMUM);
        return List.copyOf(violations);
    }

    private void addViolations(
            List<NutritionPolicyViolation> violations,
            NutrientConstraint constraint,
            double value,
            NutritionPolicyViolation below,
            NutritionPolicyViolation above
    ) {
        if (value < constraint.minimum()) {
            violations.add(below);
        }
        if (value > constraint.maximum()) {
            violations.add(above);
        }
    }
}
