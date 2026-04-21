package com.healthcare.domain.bodymeasurement.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.bodymeasurement.dto.*;
import com.healthcare.domain.bodymeasurement.service.BodyMeasurementService;
import com.healthcare.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/body-measurements")
@RequiredArgsConstructor
public class BodyMeasurementController {

    private final BodyMeasurementService measurementService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * POST /api/v1/body-measurements
     * 신체 측정 기록 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MeasurementResponse>> createMeasurement(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody CreateMeasurementRequest request) {
        Long userId = resolveUserId(bearerToken);
        MeasurementResponse response = measurementService.createMeasurement(userId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("신체 측정 기록이 저장되었습니다.", response));
    }

    /**
     * GET /api/v1/body-measurements
     * 신체 측정 기록 목록 조회 (페이징, 최신순)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<MeasurementListResponse>> listMeasurements(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = resolveUserId(bearerToken);
        Pageable pageable = PageRequest.of(page, size, Sort.by("measuredAt").descending());
        MeasurementListResponse response = measurementService.listMeasurements(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/body-measurements/range
     * 날짜 범위로 신체 측정 기록 조회 (그래프 데이터용)
     */
    @GetMapping("/range")
    public ResponseEntity<ApiResponse<List<MeasurementResponse>>> listByDateRange(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = resolveUserId(bearerToken);
        List<MeasurementResponse> response = measurementService.listMeasurementsByDateRange(userId, from, to);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/body-measurements/latest
     * 가장 최근 신체 측정 기록 조회
     */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<MeasurementResponse>> getLatestMeasurement(
            @RequestHeader("Authorization") String bearerToken) {
        Long userId = resolveUserId(bearerToken);
        MeasurementResponse response = measurementService.getLatestMeasurement(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/body-measurements/{id}
     * 신체 측정 기록 단건 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MeasurementResponse>> getMeasurement(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {
        Long userId = resolveUserId(bearerToken);
        MeasurementResponse response = measurementService.getMeasurementById(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * PATCH /api/v1/body-measurements/{id}
     * 신체 측정 기록 수정
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<MeasurementResponse>> updateMeasurement(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMeasurementRequest request) {
        Long userId = resolveUserId(bearerToken);
        MeasurementResponse response = measurementService.updateMeasurement(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("신체 측정 기록이 수정되었습니다.", response));
    }

    /**
     * DELETE /api/v1/body-measurements/{id}
     * 신체 측정 기록 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMeasurement(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {
        Long userId = resolveUserId(bearerToken);
        measurementService.deleteMeasurement(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("신체 측정 기록이 삭제되었습니다."));
    }

    private Long resolveUserId(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
