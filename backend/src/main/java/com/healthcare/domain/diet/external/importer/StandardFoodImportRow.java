package com.healthcare.domain.diet.external.importer;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StandardFoodImportRow {

    private final String foodCode;
    private final String foodName;
    private final String manufacturerName;
    private final String restaurantName;
    private final String categoryName;
    private final String nutritionServingSize;
    private final String foodSize;
    private final String calories;
    private final String protein;
    private final String carbs;
    private final String fat;
    private final String sugar;
    private final String dietaryFiber;
    private final String saturatedFat;
    private final String transFat;
    private final String cholesterol;
    private final String sodium;
    private final String dataVersion;
    private final String lastVerifiedDate;
}
