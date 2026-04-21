package com.healthcare.domain.diet.mealphoto.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.diet.mealphoto.dto.*;
import com.healthcare.domain.diet.mealphoto.service.MealPhotoAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diet/photo-analyses")
@RequiredArgsConstructor
public class MealPhotoAnalysisController {

    private final MealPhotoAnalysisService mealPhotoAnalysisService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<InitiateMealPhotoAnalysisResponse>> initiate(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody InitiateMealPhotoAnalysisRequest request
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        InitiateMealPhotoAnalysisResponse response = mealPhotoAnalysisService.initiate(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("식단 사진 업로드 준비가 완료되었습니다.", response));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ApiResponse<MealPhotoAnalysisResponse>> analyze(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AnalyzeMealPhotoRequest request
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        MealPhotoAnalysisResponse response = mealPhotoAnalysisService.analyze(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("식단 사진 분석 초안이 생성되었습니다.", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MealPhotoAnalysisResponse>> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        MealPhotoAnalysisResponse response = mealPhotoAnalysisService.get(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<ConfirmMealPhotoAnalysisResponse>> confirm(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ConfirmMealPhotoAnalysisRequest request
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ConfirmMealPhotoAnalysisResponse response = mealPhotoAnalysisService.confirm(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("식단 사진 분석 결과가 식단 기록으로 저장되었습니다.", response));
    }
}
