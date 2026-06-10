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
        boolean exhausted
) {
}
