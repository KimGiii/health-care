package com.healthcare.domain.diet.ai.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.diet.ai.dto.AiNutritionEstimateRequest;
import com.healthcare.domain.diet.ai.dto.AiNutritionEstimateResponse;
import com.healthcare.domain.diet.ai.service.AiNutritionEstimationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diet")
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.ai.meal.openai-api-key:}' != ''")
public class AiNutritionController {

    private final AiNutritionEstimationService estimationService;

    /**
     * POST /api/v1/diet/ai-estimate
     * 한국어 음식명 → AI 영양성분 추정 (공공 API 검색 결과 없을 때 폴백)
     */
    @PostMapping("/ai-estimate")
    public ResponseEntity<ApiResponse<AiNutritionEstimateResponse>> estimate(
            @Valid @RequestBody AiNutritionEstimateRequest request) {

        AiNutritionEstimateResponse response = estimationService.estimate(request.getFoodName());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
