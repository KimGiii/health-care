package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OnlinePreferencePolicy — 온라인 지표 순위 가중치")
class OnlinePreferencePolicyTest {

    @Test
    @DisplayName("전환(기록)만 있는 식품은 음수 penalty(우대)를 반환한다")
    void penaltyScore_recordedOnly_negative() {
        OnlinePreferencePolicy policy = new OnlinePreferencePolicy(
                Map.of(1L, new FoodEngagementStat(3, 0)));

        assertThat(policy.penaltyScore(food(1L))).isNegative();
    }

    @Test
    @DisplayName("다시 추천(회피)만 있는 식품은 양수 penalty(불이익)를 반환한다")
    void penaltyScore_refreshedOnly_positive() {
        OnlinePreferencePolicy policy = new OnlinePreferencePolicy(
                Map.of(1L, new FoodEngagementStat(0, 3)));

        assertThat(policy.penaltyScore(food(1L))).isPositive();
    }

    @Test
    @DisplayName("전환과 회피가 같으면 penalty가 0이다")
    void penaltyScore_balanced_zero() {
        OnlinePreferencePolicy policy = new OnlinePreferencePolicy(
                Map.of(1L, new FoodEngagementStat(2, 2)));

        assertThat(policy.penaltyScore(food(1L))).isZero();
    }

    @Test
    @DisplayName("전환이 더 많은 식품이 회피가 더 많은 식품보다 penalty가 낮다(우선된다)")
    void penaltyScore_recordedDominant_rankedBeforeRefreshedDominant() {
        OnlinePreferencePolicy policy = new OnlinePreferencePolicy(Map.of(
                1L, new FoodEngagementStat(5, 1),
                2L, new FoodEngagementStat(1, 5)));

        assertThat(policy.penaltyScore(food(1L)))
                .isLessThan(policy.penaltyScore(food(2L)));
    }

    @Test
    @DisplayName("집계 신호가 없는 식품의 penalty는 0이다")
    void penaltyScore_noStat_zero() {
        OnlinePreferencePolicy policy = new OnlinePreferencePolicy(
                Map.of(2L, new FoodEngagementStat(3, 0)));

        assertThat(policy.penaltyScore(food(1L))).isZero();
    }

    @Test
    @DisplayName("noSignals 정책은 모든 식품의 penalty가 0이다")
    void noSignals_allZero() {
        OnlinePreferencePolicy policy = OnlinePreferencePolicy.noSignals();

        assertThat(policy.penaltyScore(food(1L))).isZero();
        assertThat(policy.penaltyScore(food(2L))).isZero();
    }

    private DietRecommendationCandidate food(Long id) {
        return new DietRecommendationCandidate(
                id, "음식" + id, null, FoodCategory.GRAIN,
                250.0, 20.0, 10.0, 5.0,
                0L, id, AllergenConfidenceLevel.UNKNOWN, null, true, List.of(), false);
    }
}
