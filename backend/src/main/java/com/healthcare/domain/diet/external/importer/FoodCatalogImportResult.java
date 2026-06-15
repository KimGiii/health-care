package com.healthcare.domain.diet.external.importer;

import java.util.List;

public record FoodCatalogImportResult(
        int createdCount,
        int updatedCount,
        int skippedCount,
        List<FoodCatalogImportRejectedRow> rejectedRows
) {
    public FoodCatalogImportResult {
        rejectedRows = rejectedRows == null ? List.of() : List.copyOf(rejectedRows);
    }

    public FoodCatalogImportResult(int createdCount, int updatedCount, int skippedCount) {
        this(createdCount, updatedCount, skippedCount, List.of());
    }
}
