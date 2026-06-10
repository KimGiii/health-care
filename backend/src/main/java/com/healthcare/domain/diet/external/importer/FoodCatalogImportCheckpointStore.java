package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;

public interface FoodCatalogImportCheckpointStore {

    int nextPage(FoodCatalogSource source);

    void markPageCompleted(FoodCatalogSource source, int pageNo);
}
