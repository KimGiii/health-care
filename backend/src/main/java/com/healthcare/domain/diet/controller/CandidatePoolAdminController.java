package com.healthcare.domain.diet.controller;

import com.healthcare.common.response.ApiResponse;
import com.healthcare.common.security.AdminOperationGuard;
import com.healthcare.domain.diet.admin.CandidatePoolSummary;
import com.healthcare.domain.diet.admin.CandidatePoolSummaryService;
import com.healthcare.domain.diet.admin.VerificationPriorityEntry;
import com.healthcare.domain.diet.admin.VerificationPriorityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/diet/candidate-pool")
@RequiredArgsConstructor
public class CandidatePoolAdminController {

    private final AdminOperationGuard adminOperationGuard;
    private final CandidatePoolSummaryService summaryService;
    private final VerificationPriorityService priorityService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CandidatePoolSummary>> summary(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken) {
        adminOperationGuard.assertAllowed(adminToken);
        return ResponseEntity.ok(ApiResponse.ok("후보 풀 현황", summaryService.summarize()));
    }

    @GetMapping("/verification-priorities")
    public ResponseEntity<ApiResponse<List<VerificationPriorityEntry>>> verificationPriorities(
            @RequestHeader(value = AdminOperationGuard.HEADER_NAME, required = false) String adminToken,
            @RequestParam(defaultValue = "50") int limit) {
        adminOperationGuard.assertAllowed(adminToken);
        return ResponseEntity.ok(ApiResponse.ok("검증 우선순위 목록", priorityService.topPriorities(limit)));
    }
}
