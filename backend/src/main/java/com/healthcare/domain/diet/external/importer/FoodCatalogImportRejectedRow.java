package com.healthcare.domain.diet.external.importer;

public record FoodCatalogImportRejectedRow(
        long rowNumber,
        String field,
        String reason
) {
}
