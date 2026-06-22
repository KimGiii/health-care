package com.healthcare.domain.diet.entity;

import java.util.Set;

public enum RecommendationStatus {
    SEARCH_ONLY,
    RECOMMENDABLE,
    RECOMMENDABLE_WITH_CAUTION,
    DISABLED;

    private static final Set<RecommendationStatus> RECOMMENDATION_CANDIDATE_STATUSES = Set.of(
            RECOMMENDABLE,
            RECOMMENDABLE_WITH_CAUTION
    );

    public static Set<RecommendationStatus> recommendationCandidateStatuses() {
        return RECOMMENDATION_CANDIDATE_STATUSES;
    }

    public boolean isRecommendationCandidate() {
        return RECOMMENDATION_CANDIDATE_STATUSES.contains(this);
    }
}
