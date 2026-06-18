package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.policy.NutritionVector;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

final class RecommendationBenchmarkFixtures {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 6, 18);
    private static final List<MealType> THREE_MEALS = List.of(
            MealType.BREAKFAST,
            MealType.LUNCH,
            MealType.DINNER
    );
    private static final NutritionVector NO_INTAKE = new NutritionVector(0, 0, 0, 0);

    private RecommendationBenchmarkFixtures() {
    }

    static List<RecommendationBenchmarkScenario> v1() {
        return List.of(
                scenario(
                        "weight-loss-unverified-allergen",
                        Goal.GoalType.WEIGHT_LOSS,
                        new NutritionTargets(2000, 150, 220, 60),
                        candidates(100, 250, 20, 20, 10, AllergenConfidenceLevel.UNKNOWN),
                        NO_INTAKE,
                        true,
                        true,
                        Set.of(100.0)
                ),
                scenario(
                        "body-recomposition-after-breakfast",
                        Goal.GoalType.BODY_RECOMPOSITION,
                        new NutritionTargets(2000, 150, 220, 60),
                        candidates(200, 250, 20, 20, 10, AllergenConfidenceLevel.DIRECT_VERIFIED),
                        new NutritionVector(600, 40, 70, 20),
                        false,
                        true,
                        Set.of(100.0)
                ),
                scenario(
                        "muscle-gain-low-protein",
                        Goal.GoalType.MUSCLE_GAIN,
                        new NutritionTargets(2500, 160, 320, 70),
                        candidates(300, 250, 5, 20, 10, AllergenConfidenceLevel.DIRECT_VERIFIED),
                        NO_INTAKE,
                        false,
                        true,
                        Set.of(100.0)
                ),
                scenario(
                        "endurance-low-carbs",
                        Goal.GoalType.ENDURANCE,
                        new NutritionTargets(2800, 120, 400, 75),
                        candidates(400, 250, 10, 5, 10, AllergenConfidenceLevel.DIRECT_VERIFIED),
                        NO_INTAKE,
                        false,
                        true,
                        Set.of(100.0)
                ),
                scenario(
                        "general-health-balanced",
                        Goal.GoalType.GENERAL_HEALTH,
                        new NutritionTargets(2000, 100, 250, 65),
                        candidates(500, 200, 10, 25, 6.5, AllergenConfidenceLevel.DIRECT_VERIFIED),
                        NO_INTAKE,
                        false,
                        true,
                        Set.of(100.0, 125.0)
                ),
                scenario(
                        "general-health-incomplete-macros",
                        Goal.GoalType.GENERAL_HEALTH,
                        new NutritionTargets(2000, 100, 250, 65),
                        candidates(600, 200, 0, 0, 0, AllergenConfidenceLevel.DIRECT_VERIFIED),
                        NO_INTAKE,
                        false,
                        false,
                        Set.of(100.0, 125.0)
                )
        );
    }

    private static RecommendationBenchmarkScenario scenario(
            String id,
            Goal.GoalType goalType,
            NutritionTargets targets,
            List<DietRecommendationCandidate> candidates,
            NutritionVector confirmedIntake,
            boolean allergyRestricted,
            boolean nutrientDataComplete,
            Set<Double> allowedServingG
    ) {
        return new RecommendationBenchmarkScenario(
                id,
                FIXED_DATE,
                goalType,
                targets,
                THREE_MEALS,
                candidates,
                confirmedIntake,
                allergyRestricted,
                nutrientDataComplete,
                allowedServingG
        );
    }

    private static List<DietRecommendationCandidate> candidates(
            long idBase,
            double calories,
            double protein,
            double carbs,
            double fat,
            AllergenConfidenceLevel confidence
    ) {
        List<FoodCategory> categories = List.of(
                FoodCategory.PROTEIN_SOURCE,
                FoodCategory.GRAIN,
                FoodCategory.VEGETABLE,
                FoodCategory.FRUIT,
                FoodCategory.DAIRY,
                FoodCategory.FAT,
                FoodCategory.PROCESSED,
                FoodCategory.PROTEIN_SOURCE,
                FoodCategory.GRAIN,
                FoodCategory.VEGETABLE
        );
        boolean macroDataComplete = protein > 0.0 && carbs > 0.0 && fat > 0.0;
        return IntStream.range(0, categories.size())
                .mapToObj(index -> {
                    long id = idBase + index;
                    return new DietRecommendationCandidate(
                            id,
                            "benchmark-food-" + id,
                            null,
                            categories.get(index),
                            calories,
                            protein,
                            carbs,
                            fat,
                            0L,
                            id,
                            confidence,
                            null,
                            macroDataComplete
                    );
                })
                .toList();
    }
}
