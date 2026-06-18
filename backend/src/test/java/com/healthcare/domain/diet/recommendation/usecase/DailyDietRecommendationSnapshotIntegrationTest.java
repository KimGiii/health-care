package com.healthcare.domain.diet.recommendation.usecase;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidatePool;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidates;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.engine.DietRecommendationEngine;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationSnapshotStore;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyDietRecommendationUseCases — 스냅샷 저장 통합 테스트")
class DailyDietRecommendationSnapshotIntegrationTest {

    @Mock private UserRepository userRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private DietRestrictionRepository dietRestrictionRepository;
    @Mock private DietLogRepository dietLogRepository;
    @Mock private DietRecommendationCandidatePool candidatePool;
    @Mock private RecommendationSnapshotStore snapshotStore;
    @Spy  private DietRecommendationEngine engine = new DietRecommendationEngine();

    @InjectMocks
    private DailyDietRecommendationUseCases useCases;

    @Test
    @DisplayName("recommend() 호출 시 스냅샷이 저장되고 response에 snapshotId가 포함된다")
    void recommend_savesSnapshotAndIncludesIdInResponse() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        given(snapshotStore.save(any(), any(), any(), any(), anyBoolean())).willReturn(77L);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                new DailyDietRecommendationRequest(LocalDate.now(),
                        List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), false));

        verify(snapshotStore).save(any(), any(), any(), isNull(), anyBoolean());
        assertThat(response.snapshotId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("snapshotStore.save()는 활성 goalType과 strictAllergyMode를 전달받는다")
    void recommend_passesGoalTypeAndStrictModeToSnapshotStore() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        given(snapshotStore.save(any(), any(), any(), any(), anyBoolean())).willReturn(1L);

        useCases.recommend(1L,
                new DailyDietRecommendationRequest(LocalDate.now(),
                        List.of(MealType.LUNCH), true));

        verify(snapshotStore).save(
                org.mockito.Mockito.eq(1L),
                any(),
                any(),
                isNull(),
                org.mockito.Mockito.eq(true));
    }

    // ─── 헬퍼 ───

    private User fullProfileUser() {
        return User.builder()
                .id(1L).email("a@b.com").displayName("test")
                .sex(User.Sex.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .heightCm(175.0).weightKg(75.0)
                .activityLevel(User.ActivityLevel.MODERATELY_ACTIVE)
                .build();
    }

    private DietRecommendationCandidates candidateSet() {
        return new DietRecommendationCandidates(List.of(
                food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 250.0),
                food(2L, "현미밥",   FoodCategory.GRAIN,          250.0),
                food(3L, "브로콜리", FoodCategory.VEGETABLE,      250.0)
        ));
    }

    private DietRecommendationCandidate food(Long id, String name, FoodCategory cat, double cal) {
        return new DietRecommendationCandidate(
                id, name, null, cat, cal, 20.0, 10.0, 5.0, 0L, id,
                AllergenConfidenceLevel.UNKNOWN, null, true, List.of(), false);
    }
}
