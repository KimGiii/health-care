package com.healthcare.domain.diet.external.importer;

public record FoodCatalogImportResult(
        int createdCount,
        int updatedCount,
        int skippedCount
) {
}
