package com.healthcare.domain.diet.external.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.diet.dto.FoodCatalogResponse;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import com.healthcare.domain.diet.external.dto.ImportFoodRequest;
import com.healthcare.domain.diet.external.service.ExternalFoodSearchService;
import com.healthcare.domain.diet.external.service.FoodImportService;
import com.healthcare.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/external-foods")
@RequiredArgsConstructor
public class ExternalFoodController {

    private final ExternalFoodSearchService searchService;
    private final FoodImportService importService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * GET /api/v1/diet/external-foods/search
     * 공공데이터 포털 식품 영양정보 API 검색
     *
     * @param q      검색어 (필수)
     * @param source PUBLIC_FOOD_API | ALL (기본값: ALL)
     * @param page   0-based 페이지 (기본값: 0)
     * @param size   페이지당 결과 수 (기본값: 20)
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ExternalFoodResult>>> search(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") FoodDataSource source,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        List<ExternalFoodResult> results = searchService.search(q, source, page, size);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * POST /api/v1/diet/external-foods/import
     * 외부 식품을 내 커스텀 카탈로그에 추가
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<FoodCatalogResponse>> importFood(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody ImportFoodRequest request) {

        Long userId = resolveUserId(bearerToken);
        FoodCatalogResponse response = importService.importFood(userId, request);
        return ResponseEntity.status(201).body(
                ApiResponse.ok("외부 식품이 내 카탈로그에 추가되었습니다.", response));
    }

    private Long resolveUserId(String bearerToken) {
        return jwtTokenProvider.getUserId(bearerToken.replace("Bearer ", ""));
    }
}
