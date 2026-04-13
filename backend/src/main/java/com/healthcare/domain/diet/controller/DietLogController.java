package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.diet.dto.*;
import com.healthcare.domain.diet.service.DietLogService;
import com.healthcare.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/diet/logs")
@RequiredArgsConstructor
public class DietLogController {

    private final DietLogService dietLogService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * POST /api/v1/diet/logs
     * 식사 기록 생성 (식품 항목 포함)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateDietLogResponse>> createDietLog(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody CreateDietLogRequest request) {

        Long userId = resolveUserId(bearerToken);
        CreateDietLogResponse response = dietLogService.createDietLog(userId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("식사 기록이 저장되었습니다.", response));
    }

    /**
     * GET /api/v1/diet/logs
     * 식사 기록 목록 조회 (페이징, 날짜 범위 필터)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<DietLogListResponse>> listDietLogs(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Long userId = resolveUserId(bearerToken);
        Pageable pageable = PageRequest.of(page, size);
        DietLogListResponse response = dietLogService.listDietLogs(userId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/diet/logs/{id}
     * 식사 기록 단건 조회 (식품 항목 상세 포함)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DietLogDetailResponse>> getDietLog(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {

        Long userId = resolveUserId(bearerToken);
        DietLogDetailResponse response = dietLogService.getDietLogById(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * DELETE /api/v1/diet/logs/{id}
     * 식사 기록 소프트 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDietLog(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {

        Long userId = resolveUserId(bearerToken);
        dietLogService.deleteDietLog(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("식사 기록이 삭제되었습니다."));
    }

    private Long resolveUserId(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
