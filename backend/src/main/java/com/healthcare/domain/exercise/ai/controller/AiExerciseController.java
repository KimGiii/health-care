package com.healthcare.domain.exercise.ai.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.exercise.ai.dto.AiExerciseEstimateRequest;
import com.healthcare.domain.exercise.ai.dto.AiExerciseEstimateResponse;
import com.healthcare.domain.exercise.ai.service.AiExerciseEstimationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exercise")
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.ai.meal.openai-api-key:}' != ''")
public class AiExerciseController {

    private final AiExerciseEstimationService estimationService;

    /**
     * POST /api/v1/exercise/ai-estimate
     * 한국어 운동명 → AI 운동 정보 추정 (카탈로그 검색 결과 없을 때 폴백)
     */
    @PostMapping("/ai-estimate")
    public ResponseEntity<ApiResponse<AiExerciseEstimateResponse>> estimate(
            @Valid @RequestBody AiExerciseEstimateRequest request) {

        AiExerciseEstimateResponse response = estimationService.estimate(request.getExerciseName());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
