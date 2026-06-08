package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;

public record RecommendedFoodEntry(
        Long foodCatalogId,
        String name,
        String nameKo,
        FoodCategory category,
        double servingG,
        double calories,
        double proteinG,
        double carbsG,
        double fatG,
        AllergenConfidenceLevel allergenConfidenceLevel
) {}
