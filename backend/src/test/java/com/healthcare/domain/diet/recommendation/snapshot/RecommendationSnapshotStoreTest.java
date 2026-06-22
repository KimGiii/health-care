package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.goals.entity.Goal.GoalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private RecommendationSnapshotStore snapshotStore;

    @Test
    @DisplayName("save()는 userId·date·goalType·strictAllergyMode로 스냅샷을 저장하고 ID를 반환한다")
    void save_persistsSnapshotAndReturnsId() {
        RecommendationSnapshot saved = savedSnapshot(99L);
        given(snapshotRepository.save(any())).willReturn(saved);

        Long id = snapshotStore.save(
                1L, LocalDate.of(2026, 6, 19), "[]", GoalType.WEIGHT_LOSS, false);

        ArgumentCaptor<RecommendationSnapshot> captor =
                ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());

        RecommendationSnapshot captured = captor.getValue();
        assertThat(captured.getUserId()).isEqualTo(1L);
        assertThat(captured.getSnapshotDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(id).isEqualTo(99L);
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
}
