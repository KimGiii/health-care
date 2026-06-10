package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.springframework.stereotype.Service;

@Service
public class StandardProcessedFoodImporter extends StandardFoodCatalogImporter {

    public StandardProcessedFoodImporter(FoodCatalogRepository foodCatalogRepository) {
        super(foodCatalogRepository, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "15100066");
    }

    @Override
    protected String maker(StandardFoodImportRow row) {
        return normalize(row.getManufacturerName());
    }
}
