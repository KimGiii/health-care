package com.healthcare.domain.diet.external.importer;

import java.util.List;

@FunctionalInterface
public interface FoodCatalogPageImporter<R> {

    FoodCatalogImportResult importRows(List<R> rows);
}
