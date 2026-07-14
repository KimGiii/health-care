package com.healthcare.domain.diet.recommendation.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("재추천 피드백 (REFRESHED 이벤트) food fan-out 단위 테스트")
class RecommendationFeedbackServiceTest {

    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    @Mock
    private RecommendationEventRepository eventRepository;

    private SnapshotMealsCodec mealsCodec;
    private RecommendationFeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        mealsCodec = new SnapshotMealsCodec(new ObjectMapper());
        feedbackService = new RecommendationFeedbackService(
                snapshotRepository, eventRepository, mealsCodec);
    }

    @Test
    @DisplayName("피드백 시 스냅샷의 식품별 REFRESHED 이벤트가 fan-out 저장된다 (전 사유 저장)")
    void recordFeedback_fansOutRefreshedEventPerFood() {
        RecommendationSnapshot snapshot = snapshotWithFoods(11L, 12L);
        given(snapshotRepository.findByIdAndUserId(10L, 1L))
                .willReturn(Optional.of(snapshot));

        feedbackService.recordFeedback(1L, 10L, RecommendationFeedbackReason.NOT_HUNGRY);

        List<RecommendationEvent> events = capturedEvents();
        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getEventType()).isEqualTo(RecommendationEvent.EventType.REFRESHED);
            assertThat(e.getFeedbackReason()).isEqualTo(RecommendationFeedbackReason.NOT_HUNGRY);
            assertThat(e.getUserId()).isEqualTo(1L);
        });
        assertThat(events).extracting(RecommendationEvent::getFoodCatalogId)
                .containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    @DisplayName("mealsJson을 파싱할 수 없으면 food 없는 스냅샷 단위 REFRESHED 1행으로 폴백한다")
    void recordFeedback_unparseableMeals_fallsBackToSnapshotLevelEvent() {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                1L, LocalDate.now(), "not-json", null, false);
        given(snapshotRepository.findByIdAndUserId(10L, 1L))
                .willReturn(Optional.of(snapshot));

        feedbackService.recordFeedback(1L, 10L, RecommendationFeedbackReason.NUTRITION_DISLIKE);

        List<RecommendationEvent> events = capturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getFoodCatalogId()).isNull();
        assertThat(events.getFirst().getFeedbackReason())
                .isEqualTo(RecommendationFeedbackReason.NUTRITION_DISLIKE);
    }

    @Test
    @DisplayName("존재하지 않는 snapshotId면 ResourceNotFoundException을 던진다")
    void recordFeedback_unknownSnapshot_throws() {
        given(snapshotRepository.findByIdAndUserId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackService.recordFeedback(
                1L, 999L, RecommendationFeedbackReason.NO_REASON))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── 헬퍼 ───

    private List<RecommendationEvent> capturedEvents() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecommendationEvent>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(eventRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private RecommendationSnapshot snapshotWithFoods(Long... foodIds) {
        List<RecommendedFoodEntry> items = List.of(foodIds).stream()
                .map(id -> new RecommendedFoodEntry(
                        id, "food-" + id, "식품-" + id, null, 100, 150, 10, 15, 4, null, null))
                .toList();
        List<RecommendedMeal> meals = List.of(
                new RecommendedMeal(MealType.LUNCH, 500, 450, 30, 40, 12, items));
        return RecommendationSnapshot.create(
                1L, LocalDate.now(), mealsCodec.serialize(meals), null, false);
    }
}
