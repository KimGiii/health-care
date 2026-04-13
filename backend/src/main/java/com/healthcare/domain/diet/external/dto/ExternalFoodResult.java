package com.healthcare.domain.diet.external.dto;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalFoodResult {

    private final FoodDataSource source;
    private final String externalId;
    private final String name;
    private final String nameKo;
    private final String brand;
    private final FoodCategory category;
    private final Double caloriesPer100g;
    private final Double proteinPer100g;
    private final Double carbsPer100g;
    private final Double fatPer100g;

    public enum FoodDataSource {
        USDA, OPEN_FOOD_FACTS, ALL
    }
}
