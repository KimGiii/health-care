package com.healthcare.domain.diet.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendationStatus")
class RecommendationStatusTest {

    @Test
    @DisplayName("추천 후보 상태는 일반 추천과 주의 추천만 포함한다")
    void recommendationCandidateStatuses_includeRecommendableAndWithCaution() {
        assertThat(RecommendationStatus.recommendationCandidateStatuses())
                .containsExactlyInAnyOrder(
                        RecommendationStatus.RECOMMENDABLE,
                        RecommendationStatus.RECOMMENDABLE_WITH_CAUTION
                );
    }

    @Test
    @DisplayName("검색 전용과 비활성 상태는 추천 후보가 아니다")
    void isRecommendationCandidate_excludesSearchOnlyAndDisabled() {
        assertThat(RecommendationStatus.SEARCH_ONLY.isRecommendationCandidate()).isFalse();
        assertThat(RecommendationStatus.DISABLED.isRecommendationCandidate()).isFalse();
    }
}
