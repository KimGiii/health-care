package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationFeedbackService {

    private final RecommendationSnapshotRepository snapshotRepository;
    private final RecommendationEventRepository eventRepository;
    private final SnapshotMealsCodec mealsCodec;

    /**
     * 재추천 피드백을 스냅샷의 식품별 REFRESHED 이벤트로 fan-out 저장한다(R1 food 매핑).
     * 저장은 전 사유를 남기고, 온라인 선호 신호 산입 여부는 집계 쿼리에서 필터한다
     * ("저장은 사실, 해석은 정책"). meals_json 파싱 실패 시 food 없는 1행으로 폴백한다.
     */
    @Transactional
    public void recordFeedback(Long userId, Long snapshotId, RecommendationFeedbackReason reason) {
        RecommendationSnapshot snapshot = snapshotRepository
                .findByIdAndUserId(snapshotId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RecommendationSnapshot", snapshotId));

        List<Long> foodIds = mealsCodec.extractFoodCatalogIds(snapshot.getMealsJson());
        List<RecommendationEvent> events = foodIds.isEmpty()
                ? List.of(RecommendationEvent.refreshed(snapshotId, userId, reason, null))
                : foodIds.stream()
                        .map(foodId -> RecommendationEvent.refreshed(snapshotId, userId, reason, foodId))
                        .toList();
        eventRepository.saveAll(events);
    }
}
