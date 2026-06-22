package com.healthcare.domain.diet.restriction.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.security.CurrentUserId;
import com.healthcare.domain.diet.restriction.dto.CreateDietRestrictionRequest;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.usecase.DietRestrictionUseCases;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/restrictions")
@RequiredArgsConstructor
public class DietRestrictionController {

    private final DietRestrictionUseCases dietRestrictionUseCases;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DietRestrictionResponse>>> listRestrictions(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(dietRestrictionUseCases.listRestrictions(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DietRestrictionResponse>> createRestriction(
            @CurrentUserId Long userId,
            @Valid @RequestBody CreateDietRestrictionRequest request) {
        DietRestrictionResponse response = dietRestrictionUseCases.createRestriction(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("식단 제한 조건이 등록되었습니다.", response));
    }

    @DeleteMapping("/{restrictionId}")
    public ResponseEntity<ApiResponse<Void>> deleteRestriction(
            @CurrentUserId Long userId,
            @PathVariable Long restrictionId) {
        dietRestrictionUseCases.deleteRestriction(userId, restrictionId);
        return ResponseEntity.ok(ApiResponse.ok("식단 제한 조건이 삭제되었습니다."));
    }
}
