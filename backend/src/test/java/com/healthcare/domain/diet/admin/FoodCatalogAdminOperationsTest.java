package com.healthcare.domain.diet.admin;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportService;
import com.healthcare.domain.diet.external.importer.BrandMenuCsvImporter;
import com.healthcare.domain.diet.external.importer.FoodCatalogBatchImportSummary;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportBatchRunner;
import com.healthcare.domain.diet.external.importer.MfdsFoodNutrientDbImporter;
import com.healthcare.domain.diet.external.importer.MfdsFoodNutrientDbPageFetcher;
import com.healthcare.domain.diet.external.importer.StandardDishFoodImporter;
import com.healthcare.domain.diet.external.importer.StandardDishFoodPageFetcher;
import com.healthcare.domain.diet.external.importer.StandardProcessedFoodImporter;
import com.healthcare.domain.diet.external.importer.StandardProcessedFoodPageFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("FoodCatalogAdminOperations")
class FoodCatalogAdminOperationsTest {

    private AdminOperationGuard adminOperationGuard;
    private FoodCatalogImportBatchRunner batchRunner;
    private StandardProcessedFoodPageFetcher processedFoodPageFetcher;
    private StandardProcessedFoodImporter processedFoodImporter;
    private StandardDishFoodPageFetcher dishFoodPageFetcher;
    private StandardDishFoodImporter dishFoodImporter;
    private MfdsFoodNutrientDbPageFetcher foodNutrientDbPageFetcher;
    private MfdsFoodNutrientDbImporter foodNutrientDbImporter;
    private FoodCatalogDuplicateReportService duplicateReportService;
    private BrandMenuCsvImporter brandMenuCsvImporter;
    private FoodCatalogNameRenormalizationService nameRenormalizationService;
    private FoodCatalogNameOverrideService nameOverrideService;
    private FoodCatalogAdminOperations operations;

    @BeforeEach
    void setUp() {
        adminOperationGuard = mock(AdminOperationGuard.class);
        batchRunner = mock(FoodCatalogImportBatchRunner.class);
        processedFoodPageFetcher = mock(StandardProcessedFoodPageFetcher.class);
        processedFoodImporter = mock(StandardProcessedFoodImporter.class);
        dishFoodPageFetcher = mock(StandardDishFoodPageFetcher.class);
        dishFoodImporter = mock(StandardDishFoodImporter.class);
        foodNutrientDbPageFetcher = mock(MfdsFoodNutrientDbPageFetcher.class);
        foodNutrientDbImporter = mock(MfdsFoodNutrientDbImporter.class);
        duplicateReportService = mock(FoodCatalogDuplicateReportService.class);
        brandMenuCsvImporter = mock(BrandMenuCsvImporter.class);
        nameRenormalizationService = mock(FoodCatalogNameRenormalizationService.class);
        nameOverrideService = mock(FoodCatalogNameOverrideService.class);
        operations = new FoodCatalogAdminOperations(
                adminOperationGuard,
                batchRunner,
                processedFoodPageFetcher,
                processedFoodImporter,
                dishFoodPageFetcher,
                dishFoodImporter,
                foodNutrientDbPageFetcher,
                foodNutrientDbImporter,
                duplicateReportService,
                brandMenuCsvImporter,
                nameRenormalizationService,
                nameOverrideService
        );
    }

    @Test
    @DisplayName("가공식품 적재 — admin token 검증 후 가공식품 fetcher/importer 쌍으로 BatchRunner를 실행한다")
    void importProcessedFoods_validatesTokenAndRoutesToCorrectPair() {
        FoodCatalogBatchImportSummary expected = new FoodCatalogBatchImportSummary(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED, 1, 10, 10, 3, 4, 1, false);
        given(batchRunner.importPages(
                eq(FoodCatalogSource.MFDS_STANDARD_PROCESSED),
                eq(100),
                eq(10),
                same(processedFoodPageFetcher),
                same(processedFoodImporter)
        )).willReturn(expected);

        operations.importProcessedFoods("admin-token", 100, 10);

        verify(adminOperationGuard).assertAllowed("admin-token");
        verify(batchRunner).importPages(
                eq(FoodCatalogSource.MFDS_STANDARD_PROCESSED),
                eq(100),
                eq(10),
                same(processedFoodPageFetcher),
                same(processedFoodImporter)
        );
    }

    @Test
    @DisplayName("음식 적재 — 음식 fetcher/importer 쌍으로 BatchRunner를 실행한다")
    void importDishFoods_routesToCorrectPair() {
        FoodCatalogBatchImportSummary expected = new FoodCatalogBatchImportSummary(
                FoodCatalogSource.MFDS_STANDARD_DISH, 1, 5, 5, 20, 0, 0, true);
        given(batchRunner.importPages(
                eq(FoodCatalogSource.MFDS_STANDARD_DISH),
                eq(50),
                eq(5),
                same(dishFoodPageFetcher),
                same(dishFoodImporter)
        )).willReturn(expected);

        operations.importDishFoods("admin-token", 50, 5);

        verify(batchRunner).importPages(
                eq(FoodCatalogSource.MFDS_STANDARD_DISH),
                eq(50),
                eq(5),
                same(dishFoodPageFetcher),
                same(dishFoodImporter)
        );
    }

    @Test
    @DisplayName("영양성분DB 적재 — 영양성분 fetcher/importer 쌍으로 BatchRunner를 실행한다")
    void importNutrientDb_routesToCorrectPair() {
        FoodCatalogBatchImportSummary expected = new FoodCatalogBatchImportSummary(
                FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, 1, 2, 2, 8, 1, 0, false);
        given(batchRunner.importPages(
                eq(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB),
                eq(100),
                eq(2),
                same(foodNutrientDbPageFetcher),
                same(foodNutrientDbImporter)
        )).willReturn(expected);

        operations.importNutrientDb("admin-token", 100, 2);

        verify(batchRunner).importPages(
                eq(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB),
                eq(100),
                eq(2),
                same(foodNutrientDbPageFetcher),
                same(foodNutrientDbImporter)
        );
    }

    @Test
    @DisplayName("pageSize가 상한을 넘으면 적재를 시작하지 않는다")
    void importProcessedFoods_rejectsTooLargePageSize() {
        assertThatThrownBy(() -> operations.importProcessedFoods(
                "admin-token",
                FoodCatalogAdminOperations.MAX_PAGE_SIZE + 1,
                10
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("pageSize");

        verify(adminOperationGuard).assertAllowed("admin-token");
        verifyNoInteractions(batchRunner);
    }

    @Test
    @DisplayName("maxPages가 상한을 넘으면 적재를 시작하지 않는다")
    void importProcessedFoods_rejectsTooLargeMaxPages() {
        assertThatThrownBy(() -> operations.importProcessedFoods(
                "admin-token",
                100,
                FoodCatalogAdminOperations.MAX_IMPORT_PAGES + 1
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("maxPages");

        verify(adminOperationGuard).assertAllowed("admin-token");
        verifyNoInteractions(batchRunner);
    }

    @Test
    @DisplayName("pageSize와 maxPages는 1 이상이어야 한다")
    void importProcessedFoods_rejectsNonPositiveRequest() {
        assertThatThrownBy(() -> operations.importProcessedFoods("admin-token", 0, 10))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pageSize");

        assertThatThrownBy(() -> operations.importProcessedFoods("admin-token", 100, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maxPages");
    }

    @Test
    @DisplayName("표시명 재정규화 — admin token 검증 후 재정규화 서비스를 실행한다")
    void renormalizeNames_validatesTokenAndDelegates() {
        FoodCatalogNameRenormalizationService.RenormalizationResult expected =
                new FoodCatalogNameRenormalizationService.RenormalizationResult(1820, 4);
        given(nameRenormalizationService.renormalizeAll()).willReturn(expected);

        FoodCatalogNameRenormalizationService.RenormalizationResult result =
                operations.renormalizeNames("admin-token");

        verify(adminOperationGuard).assertAllowed("admin-token");
        verify(nameRenormalizationService).renormalizeAll();
        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(expected);
    }
}
