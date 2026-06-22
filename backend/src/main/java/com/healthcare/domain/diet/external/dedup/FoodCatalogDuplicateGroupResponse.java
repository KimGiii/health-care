package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;

import java.util.List;

public record FoodCatalogDuplicateGroupResponse(
        String normalizedKey,
        int count,
        Long suggestedCanonicalId,
        List<Entry> entries
) {
    public record Entry(
            Long id,
            String name,
            String nameKo,
            FoodCatalogSource source,
            String brandName,
            String maker,
            Double caloriesPer100g,
            int sourcePriorityRank,
            RecommendationStatus recommendationStatus
    ) {
        static Entry from(FoodCatalog catalog) {
            return new Entry(
                    catalog.getId(),
                    catalog.getName(),
                    catalog.getNameKo(),
                    catalog.getSource(),
                    catalog.getBrandName(),
                    catalog.getMaker(),
                    catalog.getCaloriesPer100g(),
                    FoodCatalogDuplicateGroup.sourcePriorityRank(catalog.getSource()),
                    catalog.getRecommendationStatus()
            );
        }
    }

    static FoodCatalogDuplicateGroupResponse from(FoodCatalogDuplicateGroup group) {
        List<Entry> entries = group.entries().stream()
                .map(Entry::from)
                .toList();
        return new FoodCatalogDuplicateGroupResponse(
                group.normalizedKey(),
                entries.size(),
                group.suggestedCanonical().getId(),
                entries);
    }
}
