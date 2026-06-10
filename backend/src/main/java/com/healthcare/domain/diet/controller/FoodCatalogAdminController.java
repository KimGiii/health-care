package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportResponse;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportService;
import com.healthcare.domain.diet.external.importer.FoodCatalogBatchImportSummary;
import com.healthcare.domain.diet.external.importer.FoodCatalogPublicDataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/diet/catalog")
@RequiredArgsConstructor
public class FoodCatalogAdminController {

    private final FoodCatalogPublicDataImportService importService;
    private final FoodCatalogDuplicateReportService duplicateReportService;

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

    @GetMapping("/dedup/report")
    public ResponseEntity<ApiResponse<FoodCatalogDuplicateReportResponse>> getDuplicateReport() {
        FoodCatalogDuplicateReportResponse report = duplicateReportService.generateReport();
        return ResponseEntity.ok(ApiResponse.ok(report));
    }
}
