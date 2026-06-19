package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;

import java.util.Set;

/**
 * 최근 섭취한 식품에 penalty를 부여해 동일 음식 반복 추천을 완화한다.
 * hard constraint가 아닌 soft objective — 후보가 부족하면 반복도 허용된다.
 */
public class UserRepetitionPolicy {

    private static final double RECENT_PENALTY = 1.0;

    private final Set<Long> recentlyConsumedFoodIds;

    public UserRepetitionPolicy(Set<Long> recentlyConsumedFoodIds) {
        this.recentlyConsumedFoodIds = recentlyConsumedFoodIds;
    }

    public double penaltyScore(DietRecommendationCandidate candidate) {
        return recentlyConsumedFoodIds.contains(candidate.foodCatalogId()) ? RECENT_PENALTY : 0.0;
    }

    public static UserRepetitionPolicy noRestrictions() {
        return new UserRepetitionPolicy(Set.of());
    }
}
