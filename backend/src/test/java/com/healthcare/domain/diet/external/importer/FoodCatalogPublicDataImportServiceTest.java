package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("FoodCatalogPublicDataImportService")
class FoodCatalogPublicDataImportServiceTest {

    @Test
    @DisplayName("가공식품 표준데이터 적재 요청을 가공식품 fetcher/importer로 실행한다")
    void importStandardProcessedFoods_routesToProcessedFetcherAndImporter() {
        FoodCatalogImportBatchRunner runner = mock(FoodCatalogImportBatchRunner.class);
        StandardProcessedFoodPageFetcher processedFetcher = mock(StandardProcessedFoodPageFetcher.class);
        StandardProcessedFoodImporter processedImporter = mock(StandardProcessedFoodImporter.class);
        StandardDishFoodPageFetcher dishFetcher = mock(StandardDishFoodPageFetcher.class);
        StandardDishFoodImporter dishImporter = mock(StandardDishFoodImporter.class);
        MfdsFoodNutrientDbPageFetcher nutrientFetcher = mock(MfdsFoodNutrientDbPageFetcher.class);
        MfdsFoodNutrientDbImporter nutrientImporter = mock(MfdsFoodNutrientDbImporter.class);
        FoodCatalogPublicDataImportService service = new FoodCatalogPublicDataImportService(
                runner,
                processedFetcher,
                processedImporter,
                dishFetcher,
                dishImporter,
                nutrientFetcher,
                nutrientImporter
        );
        FoodCatalogBatchImportSummary expected = new FoodCatalogBatchImportSummary(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                1,
                3,
                3,
                10,
                2,
                1,
                false
        );
        given(runner.importPages(
                eq(FoodCatalogSource.MFDS_STANDARD_PROCESSED),
                eq(100),
                eq(3),
                same(processedFetcher),
                same(processedImporter)
        )).willReturn(expected);

        FoodCatalogBatchImportSummary summary = service.importStandardProcessedFoods(100, 3);

        assertThat(summary).isSameAs(expected);
    }
}
