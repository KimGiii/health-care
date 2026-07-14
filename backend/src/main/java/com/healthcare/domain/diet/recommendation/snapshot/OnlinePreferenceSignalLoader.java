package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.domain.diet.recommendation.engine.FoodEngagementStat;
import com.healthcare.domain.diet.recommendation.engine.OnlinePreferencePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자 본인의 최근 30일 추천 이벤트를 식품별 참여 신호로 집계해
 * {@link OnlinePreferencePolicy}를 구성한다(R1: noSignals 대체).
 * 이벤트가 없는 사용자는 빈 신호 → 추천 결과가 기존과 완전히 동일하다(결정성 보증).
 */
@Component
@RequiredArgsConstructor
public class OnlinePreferenceSignalLoader {

    /** 반복 기피(7일)보다 긴 "취향" 수준의 느린 신호 창. 90일 보관 정책 내 안전 마진. */
    static final int LOOKBACK_DAYS = 30;

    /**
     * refreshedCount에 산입하는 음식 기인 거절 사유.
     * NOT_HUNGRY·PORTION_WRONG·NO_REASON은 음식 선호 신호가 아니므로 제외한다.
     */
    static final Set<RecommendationFeedbackReason> FOOD_DISLIKE_REASONS = Set.of(
            RecommendationFeedbackReason.RECENTLY_ATE,
            RecommendationFeedbackReason.HARD_TO_PREPARE,
            RecommendationFeedbackReason.NUTRITION_DISLIKE);

    private final RecommendationEventRepository eventRepository;

    public OnlinePreferencePolicy load(Long userId) {
        List<FoodEngagementRow> rows = eventRepository.aggregateFoodEngagement(
                userId, OffsetDateTime.now().minusDays(LOOKBACK_DAYS), FOOD_DISLIKE_REASONS);

        Map<Long, Long> recordedByFood = new HashMap<>();
        Map<Long, Long> refreshedByFood = new HashMap<>();
        for (FoodEngagementRow row : rows) {
            switch (row.eventType()) {
                case RECORDED -> recordedByFood.merge(row.foodCatalogId(), row.count(), Long::sum);
                case REFRESHED -> refreshedByFood.merge(row.foodCatalogId(), row.count(), Long::sum);
                default -> { /* GENERATED·EXPOSED는 신호 아님 — 쿼리가 이미 제외 */ }
            }
        }

        Map<Long, FoodEngagementStat> stats = new HashMap<>();
        for (Long foodId : recordedByFood.keySet()) {
            stats.put(foodId, new FoodEngagementStat(recordedByFood.get(foodId),
                    refreshedByFood.getOrDefault(foodId, 0L)));
        }
        for (Map.Entry<Long, Long> entry : refreshedByFood.entrySet()) {
            stats.putIfAbsent(entry.getKey(), new FoodEngagementStat(0L, entry.getValue()));
        }
        return new OnlinePreferencePolicy(stats);
    }
}
