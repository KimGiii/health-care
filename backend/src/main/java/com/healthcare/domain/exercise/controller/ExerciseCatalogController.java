package com.healthcare.domain.exercise.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.exercise.dto.CatalogSearchParams;
import com.healthcare.domain.exercise.dto.CreateCustomExerciseRequest;
import com.healthcare.domain.exercise.dto.ExerciseCatalogResponse;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import com.healthcare.domain.exercise.service.ExerciseCatalogService;
import com.healthcare.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exercise/catalog")
@RequiredArgsConstructor
public class ExerciseCatalogController {

    private final ExerciseCatalogService catalogService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * GET /api/v1/exercise/catalog
     * 글로벌 + 사용자 커스텀 운동 카탈로그 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExerciseCatalogResponse>>> searchCatalog(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ExerciseType exerciseType,
            @RequestParam(required = false) MuscleGroup muscleGroup,
            @RequestParam(defaultValue = "false") boolean customOnly) {

        Long userId = resolveUserId(bearerToken);
        CatalogSearchParams params = CatalogSearchParams.of(query, exerciseType, muscleGroup, customOnly);
        List<ExerciseCatalogResponse> result = catalogService.searchCatalog(userId, params);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * POST /api/v1/exercise/catalog
     * 커스텀 운동 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExerciseCatalogResponse>> createCustomExercise(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody CreateCustomExerciseRequest request) {

        Long userId = resolveUserId(bearerToken);
        ExerciseCatalogResponse response = catalogService.createCustomExercise(userId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("커스텀 운동이 등록되었습니다.", response));
    }

    private Long resolveUserId(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
