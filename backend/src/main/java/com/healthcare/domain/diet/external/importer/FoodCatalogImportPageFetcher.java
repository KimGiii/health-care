package com.healthcare.domain.diet.external.importer;

public interface FoodCatalogImportPageFetcher<R> {

    FoodCatalogImportPage<R> fetch(int pageNo, int pageSize);
}
