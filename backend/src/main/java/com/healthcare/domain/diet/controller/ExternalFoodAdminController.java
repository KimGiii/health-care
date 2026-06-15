package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.common.web.PageRequests;
import com.healthcare.domain.diet.dto.FoodCatalogResponse;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import com.healthcare.domain.diet.external.dto.ImportFoodRequest;
import com.healthcare.domain.diet.external.service.ExternalFoodSearchService;
import com.healthcare.domain.diet.external.service.FoodImportService;
import com.healthcare.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 외부 식품 검색 및 임포트 — 관리자 데이터 검증 전용.
 * 사용자 경로에서 제거됨(Phase 5).
 */
@RestController
@RequestMapping("/api/v1/admin/diet/external-foods")
@RequiredArgsConstructor
public class ExternalFoodAdminController {

    private final AdminOperationGuard adminOperationGuard;
    private final ExternalFoodSearchService searchService;
    private final FoodImportService importService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ExternalFoodResult>>> search(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") FoodDataSource source,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        adminOperationGuard.assertAllowed(adminToken);
        List<ExternalFoodResult> results = searchService.search(
                q, source, PageRequests.safePage(page), PageRequests.safeSize(size));
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<FoodCatalogResponse>> importFood(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @CurrentUserId Long userId,
            @Valid @RequestBody ImportFoodRequest request) {
        adminOperationGuard.assertAllowed(adminToken);
        FoodCatalogResponse response = importService.importFood(userId, request);
        return ResponseEntity.status(201).body(
                ApiResponse.ok("외부 식품이 카탈로그에 추가되었습니다.", response));
    }
}
