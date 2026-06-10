package com.healthcare.domain.diet.external.importer;

import java.util.List;

public interface FoodCatalogPageImporter<R> {

    FoodCatalogImportResult importRows(List<R> rows);
}
