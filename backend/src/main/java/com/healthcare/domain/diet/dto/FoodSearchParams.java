package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import lombok.Getter;

@Getter
public class FoodSearchParams {

    private final String query;
    private final FoodCategory category;
    private final boolean customOnly;

    private FoodSearchParams(String query, FoodCategory category, boolean customOnly) {
        this.query = query;
        this.category = category;
        this.customOnly = customOnly;
    }

    public static FoodSearchParams of(String query, FoodCategory category, boolean customOnly) {
        return new FoodSearchParams(query, category, customOnly);
    }
}
