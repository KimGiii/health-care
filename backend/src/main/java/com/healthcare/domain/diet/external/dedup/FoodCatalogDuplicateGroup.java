package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;

import java.util.List;

public record FoodCatalogDuplicateGroup(
        String normalizedKey,
        List<FoodCatalog> entries
) {
    static FoodCatalogDuplicateGroup of(String normalizedKey, List<FoodCatalog> entries) {
        return new FoodCatalogDuplicateGroup(normalizedKey, List.copyOf(entries));
    }
}
