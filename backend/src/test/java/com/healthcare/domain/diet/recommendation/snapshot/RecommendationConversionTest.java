package com.healthcare.domain.diet.recommendation.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationEvent.EventType;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 → 기록 전환 (RECORDED 이벤트) food 교집합 귀속 단위 테스트")
class RecommendationConversionTest {

    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    @Mock
    private RecommendationEventRepository eventRepository;

    private SnapshotMealsCodec mealsCodec;
    private RecommendationConversionService conversionService;

    @BeforeEach
    void setUp() {
        mealsCodec = new SnapshotMealsCodec(new ObjectMapper());
        conversionService = new RecommendationConversionService(
                snapshotRepository, eventRepository, mealsCodec);
    }

    @Test
    @DisplayName("기록 식품 ∩ 스냅샷 식품 교집합에만 RECORDED 이벤트를 fan-out 저장한다")
    void recordConversion_fansOutRecordedEventsForIntersection() {
        given(snapshotRepository.findByIdAndUserId(10L, 1L))
                .willReturn(Optional.of(snapshotWithFoods(11L, 12L, 13L)));
        DietLog dietLog = dietLog(99L);

        conversionService.recordConversion(1L, 10L, dietLog, List.of(11L, 13L, 777L));

        List<RecommendationEvent> events = capturedEvents();
        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getEventType()).isEqualTo(EventType.RECORDED);
            assertThat(e.getDietLogId()).isEqualTo(99L);
            assertThat(e.getSnapshotId()).isEqualTo(10L);
        });
        assertThat(events).extracting(RecommendationEvent::getFoodCatalogId)
                .containsExactlyInAnyOrder(11L, 13L);
    }

    @Test
    @DisplayName("전환 성공 시 DietLog에 recommendationSnapshotId를 연결한다")
    void recordConversion_linksSnapshotToDietLog() {
        given(snapshotRepository.findByIdAndUserId(10L, 1L))
                .willReturn(Optional.of(snapshotWithFoods(11L)));
        DietLog dietLog = dietLog(99L);

        conversionService.recordConversion(1L, 10L, dietLog, List.of(11L));

        assertThat(dietLog.getRecommendationSnapshotId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("스냅샷이 없으면(보관 만료 등) 예외 없이 스킵하고 기록을 보호한다 (best-effort)")
    void recordConversion_unknownSnapshot_skipsSilently() {
        given(snapshotRepository.findByIdAndUserId(999L, 1L))
                .willReturn(Optional.empty());
        DietLog dietLog = dietLog(42L);

        conversionService.recordConversion(1L, 999L, dietLog, List.of(11L));

        verifyNoInteractions(eventRepository);
        assertThat(dietLog.getRecommendationSnapshotId()).isNull();
    }

    @Test
    @DisplayName("교집합이 비면 food 없는 스냅샷 단위 RECORDED 1행으로 전환 사실을 보존한다")
    void recordConversion_emptyIntersection_fallsBackToSnapshotLevelEvent() {
        given(snapshotRepository.findByIdAndUserId(10L, 1L))
                .willReturn(Optional.of(snapshotWithFoods(11L, 12L)));
        DietLog dietLog = dietLog(99L);

        conversionService.recordConversion(1L, 10L, dietLog, List.of(777L));

        List<RecommendationEvent> events = capturedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getFoodCatalogId()).isNull();
        assertThat(events.getFirst().getDietLogId()).isEqualTo(99L);
    }

    // ─── 헬퍼 ───

    private List<RecommendationEvent> capturedEvents() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecommendationEvent>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(eventRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private DietLog dietLog(Long id) {
        return DietLog.builder()
                .id(id)
                .userId(1L)
                .logDate(LocalDate.now())
                .mealType(MealType.LUNCH)
                .build();
    }

    private RecommendationSnapshot snapshotWithFoods(Long... foodIds) {
        List<RecommendedFoodEntry> items = List.of(foodIds).stream()
                .map(fid -> new RecommendedFoodEntry(
                        fid, "food-" + fid, "식품-" + fid, null, 100, 150, 10, 15, 4, null, null))
                .toList();
        List<RecommendedMeal> meals = List.of(
                new RecommendedMeal(MealType.LUNCH, 500, 450, 30, 40, 12, items));
        return RecommendationSnapshot.create(
                1L, LocalDate.now(), mealsCodec.serialize(meals), null, false);
    }
}
