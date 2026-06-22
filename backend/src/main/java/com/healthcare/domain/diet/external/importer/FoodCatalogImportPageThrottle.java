package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;

public interface FoodCatalogImportPageThrottle {

    void pauseAfterPage(FoodCatalogSource source, int completedPageNo);

    static FoodCatalogImportPageThrottle none() {
        return (source, completedPageNo) -> {
        };
    }
}
