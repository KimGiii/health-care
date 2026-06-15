package com.healthcare.domain.diet.admin;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportResponse;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportService;
import com.healthcare.domain.diet.external.importer.BrandMenuCsvImporter;
import com.healthcare.domain.diet.external.importer.FoodCatalogBatchImportSummary;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportBatchRunner;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportResult;
import com.healthcare.domain.diet.external.importer.MfdsFoodNutrientDbImporter;
import com.healthcare.domain.diet.external.importer.MfdsFoodNutrientDbPageFetcher;
import com.healthcare.domain.diet.external.importer.StandardDishFoodImporter;
import com.healthcare.domain.diet.external.importer.StandardDishFoodPageFetcher;
import com.healthcare.domain.diet.external.importer.StandardProcessedFoodImporter;
import com.healthcare.domain.diet.external.importer.StandardProcessedFoodPageFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 관리자 카탈로그 작업의 operation seam.
 * 권한, 요청 상한, 실행 로그를 통과한 뒤 실제 importer/report module을 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodCatalogAdminOperations {

    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_IMPORT_PAGES = 500;

    private final AdminOperationGuard adminOperationGuard;
    private final FoodCatalogImportBatchRunner batchRunner;
    private final StandardProcessedFoodPageFetcher processedFoodPageFetcher;
    private final StandardProcessedFoodImporter processedFoodImporter;
    private final StandardDishFoodPageFetcher dishFoodPageFetcher;
    private final StandardDishFoodImporter dishFoodImporter;
    private final MfdsFoodNutrientDbPageFetcher foodNutrientDbPageFetcher;
    private final MfdsFoodNutrientDbImporter foodNutrientDbImporter;
    private final FoodCatalogDuplicateReportService duplicateReportService;
    private final BrandMenuCsvImporter brandMenuCsvImporter;

    public FoodCatalogBatchImportSummary importProcessedFoods(String adminToken, int pageSize, int maxPages) {
        assertBatchOperationAllowed(adminToken, "processed-foods", pageSize, maxPages);
        FoodCatalogBatchImportSummary summary = batchRunner.importPages(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED, pageSize, maxPages,
                processedFoodPageFetcher, processedFoodImporter);
        logBatchCompleted("processed-foods", summary);
        return summary;
    }

    public FoodCatalogBatchImportSummary importDishFoods(String adminToken, int pageSize, int maxPages) {
        assertBatchOperationAllowed(adminToken, "dish-foods", pageSize, maxPages);
        FoodCatalogBatchImportSummary summary = batchRunner.importPages(
                FoodCatalogSource.MFDS_STANDARD_DISH, pageSize, maxPages,
                dishFoodPageFetcher, dishFoodImporter);
        logBatchCompleted("dish-foods", summary);
        return summary;
    }

    public FoodCatalogBatchImportSummary importNutrientDb(String adminToken, int pageSize, int maxPages) {
        assertBatchOperationAllowed(adminToken, "nutrient-db", pageSize, maxPages);
        FoodCatalogBatchImportSummary summary = batchRunner.importPages(
                FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, pageSize, maxPages,
                foodNutrientDbPageFetcher, foodNutrientDbImporter);
        logBatchCompleted("nutrient-db", summary);
        return summary;
    }

    public FoodCatalogImportResult importBrandMenuCsv(String adminToken, InputStream csvStream) throws IOException {
        adminOperationGuard.assertAllowed(adminToken);
        log.info("관리자 카탈로그 작업 시작: operation=brand-csv");
        FoodCatalogImportResult result = brandMenuCsvImporter.importFromCsv(csvStream);
        log.info(
                "관리자 카탈로그 작업 완료: operation=brand-csv, created={}, updated={}, skipped={}",
                result.createdCount(),
                result.updatedCount(),
                result.skippedCount()
        );
        return result;
    }

    public FoodCatalogDuplicateReportResponse getDuplicateReport(String adminToken) {
        adminOperationGuard.assertAllowed(adminToken);
        log.info("관리자 카탈로그 작업 시작: operation=dedup-report");
        FoodCatalogDuplicateReportResponse report = duplicateReportService.generateReport();
        log.info(
                "관리자 카탈로그 작업 완료: operation=dedup-report, groups={}, candidates={}",
                report.totalGroups(),
                report.totalCandidates()
        );
        return report;
    }

    private void assertBatchOperationAllowed(String adminToken, String operation, int pageSize, int maxPages) {
        adminOperationGuard.assertAllowed(adminToken);
        validateBatchRequest(pageSize, maxPages);
        log.info(
                "관리자 카탈로그 작업 시작: operation={}, pageSize={}, maxPages={}",
                operation,
                pageSize,
                maxPages
        );
    }

    private void validateBatchRequest(int pageSize, int maxPages) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ValidationException("pageSize는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        if (maxPages < 1 || maxPages > MAX_IMPORT_PAGES) {
            throw new ValidationException("maxPages는 1~" + MAX_IMPORT_PAGES + " 사이여야 합니다.");
        }
    }

    private void logBatchCompleted(String operation, FoodCatalogBatchImportSummary summary) {
        log.info(
                "관리자 카탈로그 작업 완료: operation={}, source={}, startPage={}, lastCompletedPage={}, fetchedPages={}, created={}, updated={}, skipped={}, exhausted={}",
                operation,
                summary.source(),
                summary.startPage(),
                summary.lastCompletedPage(),
                summary.fetchedPageCount(),
                summary.createdCount(),
                summary.updatedCount(),
                summary.skippedCount(),
                summary.exhausted()
        );
    }
}
