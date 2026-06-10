package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportResponse;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportService;
import com.healthcare.domain.diet.external.importer.BrandMenuCsvImporter;
import com.healthcare.domain.diet.external.importer.FoodCatalogBatchImportSummary;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportResult;
import com.healthcare.domain.diet.external.importer.FoodCatalogPublicDataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin/diet/catalog")
@RequiredArgsConstructor
public class FoodCatalogAdminController {

    private final FoodCatalogPublicDataImportService importService;
    private final FoodCatalogDuplicateReportService duplicateReportService;
    private final BrandMenuCsvImporter brandMenuCsvImporter;

    @PostMapping("/import/processed-foods")
    public ResponseEntity<ApiResponse<FoodCatalogBatchImportSummary>> importProcessedFoods(
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "500") int maxPages) {
        FoodCatalogBatchImportSummary summary = importService.importStandardProcessedFoods(pageSize, maxPages);
        return ResponseEntity.ok(ApiResponse.ok("가공식품 표준데이터 적재 완료", summary));
    }

    @PostMapping("/import/dish-foods")
    public ResponseEntity<ApiResponse<FoodCatalogBatchImportSummary>> importDishFoods(
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "500") int maxPages) {
        FoodCatalogBatchImportSummary summary = importService.importStandardDishFoods(pageSize, maxPages);
        return ResponseEntity.ok(ApiResponse.ok("음식 표준데이터 적재 완료", summary));
    }

    @PostMapping("/import/nutrient-db")
    public ResponseEntity<ApiResponse<FoodCatalogBatchImportSummary>> importNutrientDb(
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "500") int maxPages) {
        FoodCatalogBatchImportSummary summary = importService.importFoodNutrientDb(pageSize, maxPages);
        return ResponseEntity.ok(ApiResponse.ok("식품영양성분DB 적재 완료", summary));
    }

    /**
     * 브랜드 공식 메뉴 CSV 파일을 업로드해 BRAND_OFFICIAL 출처로 적재한다.
     * CSV 형식: BrandMenuCsvRow.HEADERS 컬럼 순서, UTF-8 인코딩, 첫 행 헤더.
     */
    @PostMapping(value = "/import/brand-csv", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<FoodCatalogImportResult>> importBrandMenuCsv(
            @RequestParam("file") MultipartFile file) throws IOException {
        FoodCatalogImportResult result = brandMenuCsvImporter.importFromCsv(file.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok("브랜드 메뉴 CSV 적재 완료", result));
    }

    @GetMapping("/dedup/report")
    public ResponseEntity<ApiResponse<FoodCatalogDuplicateReportResponse>> getDuplicateReport() {
        FoodCatalogDuplicateReportResponse report = duplicateReportService.generateReport();
        return ResponseEntity.ok(ApiResponse.ok(report));
    }
}
