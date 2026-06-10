package com.healthcare.domain.diet.external.importer;

import java.util.List;

public record FoodCatalogImportPage<R>(
        List<R> rows,
        boolean hasNext
) {
}
