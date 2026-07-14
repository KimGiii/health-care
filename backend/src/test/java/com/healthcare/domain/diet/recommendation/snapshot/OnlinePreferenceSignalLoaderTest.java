package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.domain.diet.recommendation.engine.OnlinePreferencePolicy;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationEvent.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("온라인 선호 신호 로더 단위 테스트")
class OnlinePreferenceSignalLoaderTest {

    @Mock
    private RecommendationEventRepository eventRepository;

    @InjectMocks
    private OnlinePreferenceSignalLoader signalLoader;

    @Test
    @DisplayName("RECORDED는 우대(-), 거절 사유 REFRESHED는 불이익(+)으로 식품별 penalty를 만든다")
    void load_buildsPenaltyFromEngagementRows() {
        given(eventRepository.aggregateFoodEngagement(eq(1L), any(), anyCollection()))
                .willReturn(List.of(
                        new FoodEngagementRow(11L, EventType.RECORDED, 2L),
                        new FoodEngagementRow(12L, EventType.REFRESHED, 3L),
                        new FoodEngagementRow(13L, EventType.RECORDED, 1L),
                        new FoodEngagementRow(13L, EventType.REFRESHED, 2L)));

        OnlinePreferencePolicy policy = signalLoader.load(1L);

        assertThat(policy.penaltyScore(11L)).isEqualTo(-2.0);
        assertThat(policy.penaltyScore(12L)).isEqualTo(3.0);
        assertThat(policy.penaltyScore(13L)).isEqualTo(1.0);
        assertThat(policy.penaltyScore(999L)).isZero();
    }

    @Test
    @DisplayName("이벤트가 없으면 모든 식품 penalty가 0 — 신호 없는 사용자는 추천 결과 불변(결정성 보증)")
    void load_noEvents_returnsNeutralPolicy() {
        given(eventRepository.aggregateFoodEngagement(eq(1L), any(), anyCollection()))
                .willReturn(List.of());

        OnlinePreferencePolicy policy = signalLoader.load(1L);

        assertThat(policy.penaltyScore(11L)).isZero();
    }

    @Test
    @DisplayName("집계는 최근 30일 창 + 음식 기인 거절 사유 3종만 조회한다")
    void load_queriesThirtyDayWindowWithFoodDislikeReasons() {
        given(eventRepository.aggregateFoodEngagement(eq(1L), any(), anyCollection()))
                .willReturn(List.of());

        signalLoader.load(1L);

        ArgumentCaptor<OffsetDateTime> sinceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<RecommendationFeedbackReason>> reasonsCaptor =
                ArgumentCaptor.forClass((Class) Collection.class);
        verify(eventRepository).aggregateFoodEngagement(
                eq(1L), sinceCaptor.capture(), reasonsCaptor.capture());

        assertThat(sinceCaptor.getValue())
                .isCloseTo(OffsetDateTime.now().minusDays(30),
                        org.assertj.core.api.Assertions.within(java.time.Duration.ofMinutes(5)));
        assertThat(reasonsCaptor.getValue()).containsExactlyInAnyOrder(
                RecommendationFeedbackReason.RECENTLY_ATE,
                RecommendationFeedbackReason.HARD_TO_PREPARE,
                RecommendationFeedbackReason.NUTRITION_DISLIKE);
    }
}
