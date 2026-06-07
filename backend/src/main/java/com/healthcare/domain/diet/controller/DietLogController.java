package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.common.web.PageRequests;
import com.healthcare.domain.diet.dto.*;
import com.healthcare.domain.diet.service.DietLogService;
import com.healthcare.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public ResponseEntity<ApiResponse<CreateDietLogResponse>> createDietLog(
            @CurrentUserId Long userId,
            @Valid @RequestBody CreateDietLogRequest request) {
        CreateDietLogResponse response = dietLogService.createDietLog(userId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("식단 기록이 저장되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DietLogListResponse>> listDietLogs(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Pageable pageable = PageRequests.of(page, size);
        DietLogListResponse response = dietLogService.listDietLogs(userId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DietLogDetailResponse>> getDietLog(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        DietLogDetailResponse response = dietLogService.getDietLogById(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CreateDietLogResponse>> updateDietLog(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDietLogRequest request) {
        CreateDietLogResponse response = dietLogService.updateDietLog(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("식단 기록이 수정되었습니다.", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDietLog(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        dietLogService.deleteDietLog(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("식단 기록이 삭제되었습니다."));
    }
}
