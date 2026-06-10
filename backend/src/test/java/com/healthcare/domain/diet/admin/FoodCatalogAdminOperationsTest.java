package com.healthcare.domain.diet.admin;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportService;
import com.healthcare.domain.diet.external.importer.BrandMenuCsvImporter;
import com.healthcare.domain.diet.external.importer.FoodCatalogBatchImportSummary;
import com.healthcare.domain.diet.external.importer.FoodCatalogPublicDataImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("FoodCatalogAdminOperations")
class FoodCatalogAdminOperationsTest {

    private AdminOperationGuard adminOperationGuard;
    private FoodCatalogPublicDataImportService importService;
    private FoodCatalogDuplicateReportService duplicateReportService;
    private BrandMenuCsvImporter brandMenuCsvImporter;
    private FoodCatalogAdminOperations operations;

    @BeforeEach
    void setUp() {
        adminOperationGuard = mock(AdminOperationGuard.class);
        importService = mock(FoodCatalogPublicDataImportService.class);
        duplicateReportService = mock(FoodCatalogDuplicateReportService.class);
        brandMenuCsvImporter = mock(BrandMenuCsvImporter.class);
        operations = new FoodCatalogAdminOperations(
                adminOperationGuard,
                importService,
                duplicateReportService,
                brandMenuCsvImporter
        );
    }

    @Test
    @DisplayName("가공식품 적재 전에 admin token과 page 요청 상한을 검증한다")
    void importProcessedFoods_validatesTokenAndRoutesToImporter() {
        given(importService.importStandardProcessedFoods(100, 10))
                .willReturn(new FoodCatalogBatchImportSummary(
                        FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                        1,
                        10,
                        10,
                        3,
                        4,
                        1,
                        false
                ));

        operations.importProcessedFoods("admin-token", 100, 10);

        verify(adminOperationGuard).assertAllowed("admin-token");
        verify(importService).importStandardProcessedFoods(100, 10);
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
        verifyNoInteractions(importService);
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
        verifyNoInteractions(importService);
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
}
