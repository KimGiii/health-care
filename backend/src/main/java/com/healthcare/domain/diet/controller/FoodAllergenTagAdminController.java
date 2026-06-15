package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.allergen.FoodAllergenTagAdminOperations;
import com.healthcare.domain.diet.allergen.dto.AddAllergenTagRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAddAllergenTagsRequest;
import com.healthcare.domain.diet.allergen.dto.BulkAllergenTagResult;
import com.healthcare.domain.diet.allergen.dto.FoodAllergenTagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/diet/allergen-tags")
@RequiredArgsConstructor
public class FoodAllergenTagAdminController {

    private final FoodAllergenTagAdminOperations operations;

    @PostMapping
    public ResponseEntity<ApiResponse<FoodAllergenTagResponse>> add(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestBody AddAllergenTagRequest request) {
        FoodAllergenTagResponse response = operations.add(adminToken, request);
        return ResponseEntity.ok(ApiResponse.ok("알러젠 태그 추가 완료", response));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<BulkAllergenTagResult>> bulkAdd(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestBody BulkAddAllergenTagsRequest request) {
        BulkAllergenTagResult result = operations.bulkAdd(adminToken, request);
        return ResponseEntity.ok(ApiResponse.ok("알러젠 태그 벌크 추가 완료", result));
    }

    @GetMapping("/food/{foodCatalogId}")
    public ResponseEntity<ApiResponse<List<FoodAllergenTagResponse>>> getByFoodId(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @PathVariable Long foodCatalogId) {
        List<FoodAllergenTagResponse> tags = operations.getByFoodId(adminToken, foodCatalogId);
        return ResponseEntity.ok(ApiResponse.ok(tags));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @PathVariable Long tagId) {
        operations.delete(adminToken, tagId);
        return ResponseEntity.ok(ApiResponse.ok("알러젠 태그 삭제 완료"));
    }
}
