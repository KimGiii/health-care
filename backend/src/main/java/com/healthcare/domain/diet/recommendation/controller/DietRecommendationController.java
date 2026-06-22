package com.healthcare.domain.diet.recommendation.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.security.CurrentUserId;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.usecase.DailyDietRecommendationUseCases;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diet/recommendations")
@RequiredArgsConstructor
public class DietRecommendationController {

    private final DailyDietRecommendationUseCases dailyDietRecommendationUseCases;

    @PostMapping("/daily")
    public ResponseEntity<ApiResponse<DailyDietRecommendationResponse>> recommendDaily(
            @CurrentUserId Long userId,
            @Valid @RequestBody DailyDietRecommendationRequest request) {
        DailyDietRecommendationResponse response = dailyDietRecommendationUseCases.recommend(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
