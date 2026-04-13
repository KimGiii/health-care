package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.diet.dto.*;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.service.FoodCatalogService;
import com.healthcare.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/catalog")
@RequiredArgsConstructor
public class FoodCatalogController {

    private final FoodCatalogService foodCatalogService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * GET /api/v1/diet/catalog
     * 식품 카탈로그 검색 (글로벌 + 사용자 커스텀)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FoodCatalogResponse>>> searchFoods(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) FoodCategory category,
            @RequestParam(defaultValue = "false") boolean customOnly) {

        Long userId = resolveUserId(bearerToken);
        List<FoodCatalogResponse> response = foodCatalogService.searchFoods(
                userId, FoodSearchParams.of(query, category, customOnly));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * POST /api/v1/diet/catalog
     * 커스텀 식품 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FoodCatalogResponse>> createCustomFood(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody CreateCustomFoodRequest request) {

        Long userId = resolveUserId(bearerToken);
        FoodCatalogResponse response = foodCatalogService.createCustomFood(userId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("커스텀 식품이 등록되었습니다.", response));
    }

    private Long resolveUserId(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
