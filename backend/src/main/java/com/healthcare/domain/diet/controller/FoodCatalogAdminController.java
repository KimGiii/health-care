package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.admin.FoodCatalogAdminOperations;
import com.healthcare.domain.diet.admin.FoodCatalogNameOverrideService;
import com.healthcare.domain.diet.admin.FoodCatalogNameRenormalizationService;
import com.healthcare.domain.diet.external.dedup.FoodCatalogDuplicateReportResponse;
import com.healthcare.domain.diet.external.importer.FoodCatalogBatchImportSummary;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin/diet/catalog")
@RequiredArgsConstructor
public class FoodCatalogAdminController {

    private final FoodCatalogAdminOperations adminOperations;

    @PostMapping("/import/processed-foods")
    public ResponseEntity<ApiResponse<FoodCatalogBatchImportSummary>> importProcessedFoods(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "500") int maxPages) {
        FoodCatalogBatchImportSummary summary = adminOperations.importProcessedFoods(adminToken, pageSize, maxPages);
        return ResponseEntity.ok(ApiResponse.ok("가공식품 표준데이터 적재 완료", summary));
    }

    @PostMapping("/import/dish-foods")
    public ResponseEntity<ApiResponse<FoodCatalogBatchImportSummary>> importDishFoods(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "500") int maxPages) {
        FoodCatalogBatchImportSummary summary = adminOperations.importDishFoods(adminToken, pageSize, maxPages);
        return ResponseEntity.ok(ApiResponse.ok("음식 표준데이터 적재 완료", summary));
    }

    @PostMapping("/import/nutrient-db")
    public ResponseEntity<ApiResponse<FoodCatalogBatchImportSummary>> importNutrientDb(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "500") int maxPages) {
        FoodCatalogBatchImportSummary summary = adminOperations.importNutrientDb(adminToken, pageSize, maxPages);
        return ResponseEntity.ok(ApiResponse.ok("식품영양성분DB 적재 완료", summary));
    }

    /**
     * 브랜드 공식 메뉴 CSV 파일을 업로드해 BRAND_OFFICIAL 출처로 적재한다.
     * CSV 형식: BrandMenuCsvRow.HEADERS 컬럼 순서, UTF-8 인코딩, 첫 행 헤더.
     */
    @PostMapping(value = "/import/brand-csv", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<FoodCatalogImportResult>> importBrandMenuCsv(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam("file") MultipartFile file) throws IOException {
        FoodCatalogImportResult result = adminOperations.importBrandMenuCsv(adminToken, file.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok("브랜드 메뉴 CSV 적재 완료", result));
    }

    /**
     * 이미 적재된 MFDS 원본명({@code 경단_깨})을 자연어 표시명({@code 깨경단})으로 백필하고
     * 검색 별칭을 채운다. importer가 신규 적재를 처리하므로 기존 DB 1회 보정용이다.
     */
    @PostMapping("/renormalize-names")
    public ResponseEntity<ApiResponse<FoodCatalogNameRenormalizationService.RenormalizationResult>> renormalizeNames(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken) {
        FoodCatalogNameRenormalizationService.RenormalizationResult result =
                adminOperations.renormalizeNames(adminToken);
        return ResponseEntity.ok(ApiResponse.ok("표시명 재정규화 완료", result));
    }

    /**
     * 검수 CSV({@code raw_name,heuristic_display,corrected_display})를 업로드해
     * 사람이 교정한 표시명을 휴리스틱보다 우선 적용한다. {@code corrected_display}가 빈 행은 건너뛴다.
     */
    @PostMapping(value = "/name-overrides", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<FoodCatalogNameOverrideService.OverrideResult>> applyNameOverrides(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam("file") MultipartFile file) throws IOException {
        FoodCatalogNameOverrideService.OverrideResult result =
                adminOperations.applyNameOverrides(adminToken, file.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok("표시명 수동 오버라이드 완료", result));
    }

    @GetMapping("/dedup/report")
    public ResponseEntity<ApiResponse<FoodCatalogDuplicateReportResponse>> getDuplicateReport(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken) {
        FoodCatalogDuplicateReportResponse report = adminOperations.getDuplicateReport(adminToken);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }
}
