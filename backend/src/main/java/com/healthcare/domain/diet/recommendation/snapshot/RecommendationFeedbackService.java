package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationFeedbackService {

    private final RecommendationSnapshotRepository snapshotRepository;
    private final RecommendationEventRepository eventRepository;

    @Transactional
    public void recordFeedback(Long userId, Long snapshotId, RecommendationFeedbackReason reason) {
        RecommendationSnapshot snapshot = snapshotRepository
                .findByIdAndUserId(snapshotId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RecommendationSnapshot", snapshotId));

        snapshot.recordRefreshed(reason);
        RecommendationEvent event = snapshot.getEvents().getLast();
        eventRepository.save(event);
    }
}
