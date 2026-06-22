package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.springframework.stereotype.Service;

@Service
public class StandardDishFoodImporter extends StandardFoodCatalogImporter {

    public StandardDishFoodImporter(FoodCatalogIngestService ingestService) {
        super(ingestService, FoodCatalogSource.MFDS_STANDARD_DISH, "15100070");
    }

    @Override
    protected String brandName(StandardFoodImportRow row) {
        return normalize(row.getRestaurantName());
    }

    @Override
    protected String maker(StandardFoodImportRow row) {
        return normalize(row.getRestaurantName());
    }
}
