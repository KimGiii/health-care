package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.admin.FoodCatalogAdminOperations;
import com.healthcare.domain.diet.admin.FoodCatalogNameOverrideService;
import com.healthcare.domain.diet.admin.FoodCatalogNameRenormalizationService;
import com.healthcare.domain.diet.external.dedup.DedupCollisionQueueResponse;
import com.healthcare.domain.diet.external.dedup.FoodCatalogCanonicalBackfillSummary;
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
     * 기존 카탈로그 row를 source + food_code 기준으로 추천 후보 승격/강등한다.
     * CSV 형식: RecommendationCurationCsvRow.HEADERS 컬럼 순서, UTF-8 인코딩, 첫 행 헤더.
     */
    @PostMapping(value = "/curation-csv", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<FoodCatalogImportResult>> importRecommendationCurationCsv(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam("file") MultipartFile file) throws IOException {
        FoodCatalogImportResult result =
                adminOperations.importRecommendationCurationCsv(adminToken, file.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok("추천 큐레이션 CSV 반영 완료", result));
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

    /**
     * 같은 코드·다른 이름 충돌(COLLISION) 대표 행을 코드 단위 검토 큐로 조회한다(설계 §5·§9).
     * {@code afterCode}(코드 커서)와 {@code limit}으로 페이지네이션한다 — 응답의 {@code nextCursor}로 다음 페이지를 잇는다.
     */
    @GetMapping("/dedup/collisions")
    public ResponseEntity<ApiResponse<DedupCollisionQueueResponse>> getCollisionReviewQueue(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam(value = "afterCode", required = false) String afterCode,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        DedupCollisionQueueResponse response =
                adminOperations.getCollisionReviewQueue(adminToken, afterCode, limit);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 기존 DB의 dedup 미정규화 행을 출처 우선순위 canonical로 1회 수렴시킨다(설계 §7 백필 패스).
     * 전량 적재 전, V39 이후 각 출처가 독립 적재돼 모두 대표로 남은 행을 정규화한다(멱등·재실행 안전).
     */
    @PostMapping("/dedup/backfill")
    public ResponseEntity<ApiResponse<FoodCatalogCanonicalBackfillSummary>> backfillCanonicalDedup(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken) {
        FoodCatalogCanonicalBackfillSummary summary = adminOperations.backfillCanonicalDedup(adminToken);
        return ResponseEntity.ok(ApiResponse.ok("canonical dedup 백필 완료", summary));
    }
}
