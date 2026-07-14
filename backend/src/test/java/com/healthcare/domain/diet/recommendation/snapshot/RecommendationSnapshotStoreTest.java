package com.healthcare.domain.diet.recommendation.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.goals.entity.Goal.GoalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationSnapshotStore 단위 테스트")
class RecommendationSnapshotStoreTest {

    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    @Mock
    private RecommendationEventRepository eventRepository;

    private RecommendationSnapshotStore snapshotStore;

    @BeforeEach
    void setUp() {
        snapshotStore = new RecommendationSnapshotStore(
                snapshotRepository, eventRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("save()는 userId·date·goalType·strictAllergyMode로 스냅샷을 저장하고 ID를 반환한다")
    void save_persistsSnapshotAndReturnsId() {
        RecommendationSnapshot saved = savedSnapshot(99L);
        given(snapshotRepository.save(any())).willReturn(saved);

        Long id = snapshotStore.save(
                1L, LocalDate.of(2026, 6, 19), List.of(), GoalType.WEIGHT_LOSS, false);

        ArgumentCaptor<RecommendationSnapshot> captor =
                ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());

        RecommendationSnapshot captured = captor.getValue();
        assertThat(captured.getUserId()).isEqualTo(1L);
        assertThat(captured.getSnapshotDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(captured.getMealsJson()).isEqualTo("[]");
        assertThat(id).isEqualTo(99L);
    }

    @Test
    @DisplayName("save()는 스냅샷의 식품별로 GENERATED 이벤트를 fan-out 저장한다 (중복 식품은 1회)")
    void save_persistsGeneratedEventsPerDistinctFood() {
        given(snapshotRepository.save(any())).willReturn(savedSnapshot(99L));
        List<RecommendedMeal> meals = List.of(
                meal(MealType.BREAKFAST, entry(11L), entry(12L)),
                meal(MealType.LUNCH, entry(11L), entry(13L)));

        snapshotStore.save(1L, LocalDate.of(2026, 7, 14), meals, GoalType.WEIGHT_LOSS, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecommendationEvent>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(eventRepository).saveAll(captor.capture());

        List<RecommendationEvent> events = captor.getValue();
        assertThat(events).hasSize(3);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getEventType()).isEqualTo(RecommendationEvent.EventType.GENERATED);
            assertThat(e.getSnapshotId()).isEqualTo(99L);
            assertThat(e.getUserId()).isEqualTo(1L);
        });
        assertThat(events).extracting(RecommendationEvent::getFoodCatalogId)
                .containsExactlyInAnyOrder(11L, 12L, 13L);
    }

    // ─── 헬퍼 ───

    private RecommendationSnapshot savedSnapshot(Long id) {
        RecommendationSnapshot s = RecommendationSnapshot.create(
                1L, LocalDate.now(), "[]", null, false);
        try {
            var f = RecommendationSnapshot.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    private RecommendedMeal meal(MealType mealType, RecommendedFoodEntry... items) {
        return new RecommendedMeal(mealType, 500, 450, 30, 40, 12, List.of(items));
    }

    private RecommendedFoodEntry entry(Long foodCatalogId) {
        return new RecommendedFoodEntry(
                foodCatalogId, "food-" + foodCatalogId, "식품-" + foodCatalogId,
                null, 100, 150, 10, 15, 4, null, null);
    }
}
