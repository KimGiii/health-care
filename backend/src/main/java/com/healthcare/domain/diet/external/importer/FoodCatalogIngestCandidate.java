package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;

record FoodCatalogIngestCandidate(
        FoodCatalog food,
        FoodCatalogImportRejectedRow rejection
) {
    static FoodCatalogIngestCandidate accepted(FoodCatalog food) {
        return new FoodCatalogIngestCandidate(food, null);
    }

    static FoodCatalogIngestCandidate skipped() {
        return new FoodCatalogIngestCandidate(null, null);
    }

    static FoodCatalogIngestCandidate rejected(long rowNumber, String field, String reason) {
        return new FoodCatalogIngestCandidate(
                null,
                new FoodCatalogImportRejectedRow(rowNumber, field, reason)
        );
    }

    boolean accepted() {
        return food != null;
    }
}
