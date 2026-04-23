package com.healthcare.domain.insights.controller;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.common.response.ApiResponse;
import com.healthcare.domain.insights.dto.ChangeAnalysisResponse;
import com.healthcare.domain.insights.dto.WeeklySummaryResponse;
import com.healthcare.domain.insights.service.InsightsService;
import com.healthcare.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/weekly-summary")
    public ResponseEntity<ApiResponse<WeeklySummaryResponse>> getWeeklySummary(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @RequestParam(defaultValue = "0") int weekOffset) {
        Long userId = resolveUserId(bearerToken);
        return ResponseEntity.ok(ApiResponse.ok(insightsService.getWeeklySummary(userId, weekOffset)));
    }

    @GetMapping("/change-analysis")
    public ResponseEntity<ApiResponse<ChangeAnalysisResponse>> getChangeAnalysis(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @RequestParam String from,
            @RequestParam String to) {
        Long userId = resolveUserId(bearerToken);
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(from);
            toDate = LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleViolationException("날짜 형식이 올바르지 않습니다. yyyy-MM-dd 형식으로 입력해 주세요.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessRuleViolationException("시작 날짜는 종료 날짜보다 이전이어야 합니다.");
        }
        return ResponseEntity.ok(ApiResponse.ok(insightsService.getChangeAnalysis(userId, fromDate, toDate)));
    }

    private Long resolveUserId(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new UnauthorizedException("유효하지 않은 인증 형식입니다.");
        }
        return jwtTokenProvider.getUserId(bearerToken.substring(7));
    }
}
