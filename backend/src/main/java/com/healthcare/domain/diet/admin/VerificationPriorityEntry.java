package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;

public record VerificationPriorityEntry(
        Long foodCatalogId,
        String name,
        FoodCategory category,
        long usageCount,
        double priorityScore
) {}
