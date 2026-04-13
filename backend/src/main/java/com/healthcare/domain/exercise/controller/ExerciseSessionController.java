package com.healthcare.domain.exercise.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.exercise.dto.*;
import com.healthcare.domain.exercise.service.ExerciseSessionService;
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
@RequestMapping("/api/v1/exercise/sessions")
@RequiredArgsConstructor
public class ExerciseSessionController {

    private final ExerciseSessionService sessionService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * POST /api/v1/exercise/sessions
     * 운동 세션 생성 (세트 포함)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateSessionResponse>> createSession(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody CreateSessionRequest request) {

        Long userId = resolveUserId(bearerToken);
        CreateSessionResponse response = sessionService.createSession(userId, request);

        String message = response.getNewPersonalRecords().isEmpty()
                ? "운동 세션이 저장되었습니다."
                : "운동 세션이 저장되었습니다. 새로운 개인 최고 기록 " + response.getNewPersonalRecords().size() + "개!";

        return ResponseEntity.status(201).body(ApiResponse.ok(message, response));
    }

    /**
     * GET /api/v1/exercise/sessions
     * 운동 세션 목록 조회 (페이징, 날짜 범위 필터)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SessionListResponse>> listSessions(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Long userId = resolveUserId(bearerToken);
        Pageable pageable = PageRequest.of(page, size);
        SessionListResponse response = sessionService.listSessions(userId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/exercise/sessions/{id}
     * 운동 세션 단건 조회 (세트 상세 포함)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionDetailResponse>> getSession(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {

        Long userId = resolveUserId(bearerToken);
        SessionDetailResponse response = sessionService.getSessionById(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * DELETE /api/v1/exercise/sessions/{id}
     * 운동 세션 소프트 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {

        Long userId = resolveUserId(bearerToken);
        sessionService.deleteSession(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("운동 세션이 삭제되었습니다."));
    }

    private Long resolveUserId(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
