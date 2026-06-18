package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.policy.NutritionVector;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

record RecommendationBenchmarkScenario(
        String id,
        LocalDate date,
        Goal.GoalType goalType,
        NutritionTargets targets,
        List<MealType> mealTypes,
        List<DietRecommendationCandidate> candidates,
        NutritionVector confirmedIntake,
        boolean allergyRestricted,
        Set<Double> allowedServingG
) {
}
