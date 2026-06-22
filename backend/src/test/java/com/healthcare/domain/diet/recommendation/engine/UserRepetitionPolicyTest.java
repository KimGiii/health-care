package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserRepetitionPolicy — 최근 반복 기피 정책")
class UserRepetitionPolicyTest {

    @Test
    @DisplayName("최근 섭취 목록에 없는 식품의 penalty는 0이다")
    void penaltyScore_notRecentlyConsumed_returnsZero() {
        UserRepetitionPolicy policy = new UserRepetitionPolicy(Set.of(2L, 3L));

        double penalty = policy.penaltyScore(food(1L));

        assertThat(penalty).isEqualTo(0.0);
    }

    @Test
    @DisplayName("최근 섭취한 식품은 양수 penalty를 반환한다")
    void penaltyScore_recentlyConsumed_returnsPositivePenalty() {
        UserRepetitionPolicy policy = new UserRepetitionPolicy(Set.of(1L, 2L));

        double penalty = policy.penaltyScore(food(1L));

        assertThat(penalty).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("최근 섭취 목록이 비어있으면 모든 식품의 penalty가 0이다")
    void penaltyScore_emptyRecentSet_allZero() {
        UserRepetitionPolicy policy = new UserRepetitionPolicy(Set.of());

        assertThat(policy.penaltyScore(food(1L))).isEqualTo(0.0);
        assertThat(policy.penaltyScore(food(2L))).isEqualTo(0.0);
    }

    // 반복 페널티가 추천 결과에 실제로 반영되는지(정렬 융합)는
    // DietRecommendationEngineTest.RepetitionPreference 에서 엔진 출력으로 검증한다.

    // ─── 헬퍼 ───

    private DietRecommendationCandidate food(Long id) {
        return new DietRecommendationCandidate(
                id, "음식" + id, null, FoodCategory.GRAIN,
                250.0, 20.0, 10.0, 5.0,
                0L, id, AllergenConfidenceLevel.UNKNOWN, null, true, List.of(), false);
    }
}
