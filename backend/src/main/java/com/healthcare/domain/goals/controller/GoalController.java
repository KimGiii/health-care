package com.healthcare.domain.goals.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.goals.dto.*;
import com.healthcare.domain.goals.entity.Goal.GoalStatus;
import com.healthcare.domain.goals.service.GoalService;
import com.healthcare.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * POST /api/v1/goals
     * 새 목표 생성 — 기존 ACTIVE 목표는 자동으로 ABANDONED 처리
     */
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody CreateGoalRequest request) {
        Long userId = resolveUserId(bearerToken);
        GoalResponse response = goalService.createGoal(userId, request);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok("목표가 생성되었습니다. 칼로리 및 영양소 목표가 업데이트되었습니다.", response));
    }

    /**
     * GET /api/v1/goals
     * 목표 목록 조회 (페이징, 상태 필터)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<GoalListResponse>> listGoals(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(required = false) GoalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = resolveUserId(bearerToken);
        Pageable pageable = PageRequest.of(page, size);
        GoalListResponse response = goalService.listGoals(userId, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/goals/{id}
     * 목표 단건 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GoalResponse>> getGoal(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {
        Long userId = resolveUserId(bearerToken);
        GoalResponse response = goalService.getGoalById(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * PATCH /api/v1/goals/{id}
     * 목표 수정 (목표값, 목표날짜, 주간 목표속도)
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<GoalResponse>> updateGoal(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id,
            @RequestBody UpdateGoalRequest request) {
        Long userId = resolveUserId(bearerToken);
        GoalResponse response = goalService.updateGoal(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/goals/{id}/progress
     * 목표 진행률 조회 — 현재 체중/체지방 등 신체 측정값 기반 진행 계산
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> getGoalProgress(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {
        Long userId = resolveUserId(bearerToken);
        GoalProgressResponse response = goalService.getGoalProgress(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * DELETE /api/v1/goals/{id}
     * 목표 포기 (ABANDONED) — 히스토리는 유지
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> abandonGoal(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {
        Long userId = resolveUserId(bearerToken);
        goalService.abandonGoal(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("목표가 포기 처리되었습니다. 목표 히스토리에서 확인하실 수 있습니다."));
    }

    private Long resolveUserId(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
