package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public record FoodCatalogDuplicateGroup(
        String normalizedKey,
        FoodCatalog suggestedCanonical,
        List<FoodCatalog> entries
) {
    static FoodCatalogDuplicateGroup of(String normalizedKey, List<FoodCatalog> entries) {
        List<FoodCatalog> immutableEntries = List.copyOf(entries);
        return new FoodCatalogDuplicateGroup(
                normalizedKey,
                suggestCanonical(immutableEntries),
                immutableEntries);
    }

    private static FoodCatalog suggestCanonical(List<FoodCatalog> entries) {
        return entries.stream()
                .min(Comparator
                        .comparingInt((FoodCatalog food) -> sourcePriorityRank(food.getSource()))
                        .thenComparing(FoodCatalogDuplicateGroup::lastVerifiedAtForSort,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .orElseThrow();
    }

    private static OffsetDateTime lastVerifiedAtForSort(FoodCatalog food) {
        return food.getLastVerifiedAt();
    }

    static int sourcePriorityRank(FoodCatalogSource source) {
        return switch (source) {
            case BRAND_OFFICIAL -> 1;
            case MFDS_STANDARD_DISH -> 2;
            case MFDS_STANDARD_PROCESSED -> 3;
            case MFDS_FOOD_NUTRIENT_DB -> 4;
            case SEED -> 5;
            case USER_CUSTOM -> 6;
        };
    }
}
