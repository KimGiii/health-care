package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;

public record FoodCatalogBatchImportSummary(
        FoodCatalogSource source,
        int startPage,
        int lastCompletedPage,
        int fetchedPageCount,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int attemptedCount,
        double skippedRatio,
        boolean exhausted
) {
    public FoodCatalogBatchImportSummary {
        attemptedCount = createdCount + updatedCount + skippedCount;
        skippedRatio = attemptedCount == 0 ? 0.0 : (double) skippedCount / attemptedCount;
    }

    public FoodCatalogBatchImportSummary(
            FoodCatalogSource source,
            int startPage,
            int lastCompletedPage,
            int fetchedPageCount,
            int createdCount,
            int updatedCount,
            int skippedCount,
            boolean exhausted) {
        this(source, startPage, lastCompletedPage, fetchedPageCount, createdCount, updatedCount, skippedCount,
                0, 0.0, exhausted);
    }
}
