package com.healthcare.domain.diet.recommendation.controller;

import com.healthcare.domain.diet.recommendation.snapshot.RecommendationFeedbackReason;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationFeedbackService;
import com.healthcare.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diet/recommendations")
@RequiredArgsConstructor
public class RecommendationFeedbackController {

    private final RecommendationFeedbackService feedbackService;

    @PostMapping("/{snapshotId}/feedback")
    public ResponseEntity<Void> postFeedback(
            @CurrentUserId Long userId,
            @PathVariable Long snapshotId,
            @Valid @RequestBody FeedbackRequest request) {
        feedbackService.recordFeedback(userId, snapshotId, request.reason());
        return ResponseEntity.noContent().build();
    }

    public record FeedbackRequest(@NotNull RecommendationFeedbackReason reason) {}
}
