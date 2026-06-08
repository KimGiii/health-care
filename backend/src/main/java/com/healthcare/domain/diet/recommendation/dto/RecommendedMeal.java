package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.entity.DietLog.MealType;

import java.util.List;

public record RecommendedMeal(
        MealType mealType,
        double targetCalories,
        double totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        List<RecommendedFoodEntry> items
) {}
