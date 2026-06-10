package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FoodCatalogPublicDataImportService {

    private final FoodCatalogImportBatchRunner runner;
    private final StandardProcessedFoodPageFetcher processedFoodPageFetcher;
    private final StandardProcessedFoodImporter processedFoodImporter;
    private final StandardDishFoodPageFetcher dishFoodPageFetcher;
    private final StandardDishFoodImporter dishFoodImporter;
    private final MfdsFoodNutrientDbPageFetcher foodNutrientDbPageFetcher;
    private final MfdsFoodNutrientDbImporter foodNutrientDbImporter;

    public FoodCatalogBatchImportSummary importStandardProcessedFoods(int pageSize, int maxPages) {
        return runner.importPages(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                pageSize,
                maxPages,
                processedFoodPageFetcher,
                processedFoodImporter
        );
    }

    public FoodCatalogBatchImportSummary importStandardDishFoods(int pageSize, int maxPages) {
        return runner.importPages(
                FoodCatalogSource.MFDS_STANDARD_DISH,
                pageSize,
                maxPages,
                dishFoodPageFetcher,
                dishFoodImporter
        );
    }

    public FoodCatalogBatchImportSummary importFoodNutrientDb(int pageSize, int maxPages) {
        return runner.importPages(
                FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB,
                pageSize,
                maxPages,
                foodNutrientDbPageFetcher,
                foodNutrientDbImporter
        );
    }
}
