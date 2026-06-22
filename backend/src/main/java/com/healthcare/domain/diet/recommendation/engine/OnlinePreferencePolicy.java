package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;

import java.util.Map;

/**
 * 온라인 참여 지표(추천→기록 전환, 다시 추천)를 식품별 순위 신호로 변환하는 soft objective 정책.
 *
 * <p>{@link UserRepetitionPolicy}와 동일하게 penalty 형태(낮을수록 우대)를 반환해 엔진 점수화 seam에
 * 합류할 수 있다. 전환이 많은 식품은 음수(우대), 다시 추천으로 회피된 식품은 양수(불이익)가 된다.
 *
 * <p>가중치({@code RECORD_WEIGHT}, {@code REFRESH_WEIGHT})는 초기 가설값이다. 실제 가중치 튜닝은
 * 운영 이벤트가 쌓인 뒤 benchmark·온라인 지표로 조정한다. (추천 최적화 계획 §11~§12 Phase 5)
 * hard constraint가 아니므로 신호가 없으면 추천 결과에 영향을 주지 않는다.
 */
public class OnlinePreferencePolicy {

    private static final double RECORD_WEIGHT = 1.0;
    private static final double REFRESH_WEIGHT = 1.0;

    private final Map<Long, FoodEngagementStat> statsByFoodId;

    public OnlinePreferencePolicy(Map<Long, FoodEngagementStat> statsByFoodId) {
        this.statsByFoodId = Map.copyOf(statsByFoodId);
    }

    public double penaltyScore(DietRecommendationCandidate candidate) {
        return penaltyScore(candidate.foodCatalogId());
    }

    public double penaltyScore(long foodCatalogId) {
        FoodEngagementStat stat = statsByFoodId.get(foodCatalogId);
        if (stat == null) {
            return 0.0;
        }
        return REFRESH_WEIGHT * stat.refreshedCount() - RECORD_WEIGHT * stat.recordedCount();
    }

    public static OnlinePreferencePolicy noSignals() {
        return new OnlinePreferencePolicy(Map.of());
    }
}
