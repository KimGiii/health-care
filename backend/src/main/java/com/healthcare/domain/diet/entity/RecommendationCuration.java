package com.healthcare.domain.diet.entity;

public sealed interface RecommendationCuration
        permits RecommendationCuration.Recommendable,
                RecommendationCuration.WithCaution,
                RecommendationCuration.SearchOnly,
                RecommendationCuration.Disabled {

    RecommendationStatus status();

    record Recommendable() implements RecommendationCuration {
        public RecommendationStatus status() { return RecommendationStatus.RECOMMENDABLE; }
    }

    record WithCaution(String reason) implements RecommendationCuration {
        public WithCaution {
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("WithCaution requires a non-blank reason");
        }
        public RecommendationStatus status() { return RecommendationStatus.RECOMMENDABLE_WITH_CAUTION; }
    }

    record SearchOnly() implements RecommendationCuration {
        public RecommendationStatus status() { return RecommendationStatus.SEARCH_ONLY; }
    }

    record Disabled() implements RecommendationCuration {
        public RecommendationStatus status() { return RecommendationStatus.DISABLED; }
    }

    static RecommendationCuration of(RecommendationStatus status, String reason) {
        return switch (status) {
            case RECOMMENDABLE -> new Recommendable();
            case RECOMMENDABLE_WITH_CAUTION -> new WithCaution(reason);
            case SEARCH_ONLY -> new SearchOnly();
            case DISABLED -> new Disabled();
        };
    }
}
