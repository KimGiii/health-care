package com.healthcare.domain.diet.ai.dto;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiNutritionEstimateResponse {

    private final String foodName;
    private final FoodCategory category;
    private final Double caloriesPer100g;
    private final Double proteinPer100g;
    private final Double carbsPer100g;
    private final Double fatPer100g;
    private final Double confidence;
    private final String disclaimer;
    private final boolean isAiEstimated;
}
