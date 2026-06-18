package com.healthcare.domain.diet.recommendation.usecase;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidatePool;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidates;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.engine.DietRecommendationEngine;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyDietRecommendationUseCases 단위 테스트")
class DailyDietRecommendationUseCasesTest {

    @Mock private UserRepository userRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private DietRestrictionRepository dietRestrictionRepository;
    @Mock private DietLogRepository dietLogRepository;
    @Mock private DietRecommendationCandidatePool candidatePool;
    @Spy private DietRecommendationEngine engine = new DietRecommendationEngine();

    @InjectMocks
    private DailyDietRecommendationUseCases useCases;

    @Test
    @DisplayName("필수 프로필 정보가 없으면 BusinessRuleViolationException을 던진다")
    void recommend_incompleteProfile_throws() {
        User user = User.builder().id(1L).email("a@b.com").displayName("test").build(); // 프로필 미완
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));

        var request = request(List.of(MealType.BREAKFAST, MealType.LUNCH));
        assertThatThrownBy(() -> useCases.recommend(1L, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("프로필");
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 BusinessRuleViolationException을 던진다")
    void recommend_userNotFound_throws() {
        given(userRepository.findByIdAndDeletedAtIsNull(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.recommend(99L, request(List.of(MealType.LUNCH))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("프로필이 완전하면 추천을 생성하고 응답을 반환한다")
    void recommend_completeProfile_returnsRecommendation() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)));

        assertThat(response).isNotNull();
        assertThat(response.meals()).hasSize(3);
        assertThat(response.appliedRestrictions()).isEmpty();
    }

    @Test
    @DisplayName("추천 후보 풀 Module에서 후보를 로드한다")
    void recommend_loadsCandidatesFromCandidatePool() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());

        useCases.recommend(1L, request(List.of(MealType.BREAKFAST)));

        verify(candidatePool).load(List.of(), false);
    }

    @Test
    @DisplayName("알러젠 제한이 있으면 응답에 적용된 제한 조건이 포함된다")
    void recommend_withRestrictions_appliedRestrictionsInResponse() {
        User user = fullProfileUser();
        DietRestriction restriction = DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(RestrictionType.ALLERGY)
                .targetType(TargetType.ALLERGEN_TAG).allergenTag(AllergenTag.MILK)
                .build();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of(restriction));
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.LUNCH)));

        assertThat(response.appliedRestrictions()).hasSize(1);
    }

    @Test
    @DisplayName("제한 적용 후 추천 후보가 없으면 BusinessRuleViolationException을 던진다")
    void recommend_noCandidates_throws() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean()))
                .willReturn(new DietRecommendationCandidates(List.of()));

        assertThatThrownBy(() -> useCases.recommend(1L, request(List.of(MealType.BREAKFAST))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("후보");
    }

    @Test
    @DisplayName("추천 결과가 영양 목표를 충족하지 못하면 failureReason이 포함된 응답을 반환한다")
    void recommend_targetMismatch_returnsFailureReason() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean()))
                .willReturn(new DietRecommendationCandidates(List.of(
                        food(1L, "오이", FoodCategory.VEGETABLE, 1.0)
                )));

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST)));

        assertThat(response.succeeded()).isFalse();
        assertThat(response.failureReason()).isNotBlank();
    }

    @Test
    @DisplayName("당일 이미 섭취한 영양량이 있으면 응답에 남은 목표가 반영된다")
    void recommend_withConsumedNutrients_remainingTargetsReflected() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());

        // 아침에 이미 500kcal 섭취한 기록
        DietLog breakfastLog = DietLog.builder()
                .userId(1L).logDate(LocalDate.now()).mealType(MealType.BREAKFAST)
                .totalCalories(500.0).totalProteinG(30.0).totalCarbsG(60.0).totalFatG(15.0)
                .build();
        given(dietLogRepository.findByUserIdAndLogDate(1L, LocalDate.now()))
                .willReturn(List.of(breakfastLog));
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.LUNCH, MealType.DINNER)));

        // 응답에 남은 목표가 포함되어야 한다
        assertThat(response.remainingTargets()).isNotNull();
        assertThat(response.remainingTargets().calorieTarget())
                .isLessThan(response.targets().calorieTarget());
    }

    @Test
    @DisplayName("alternativeCount=2 요청 시 응답에 2개의 대안 식단이 포함된다")
    void recommend_alternativeCount2_responseContainsTwoAlternatives() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                requestWithAlternatives(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 2));

        assertThat(response.alternatives()).hasSize(2);
    }

    @Test
    @DisplayName("alternativeCount=0(기본값) 요청 시 alternatives는 빈 리스트다")
    void recommend_defaultRequest_alternativesEmpty() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)));

        assertThat(response.alternatives()).isEmpty();
    }

    @Test
    @DisplayName("당일 섭취 기록이 없으면 남은 목표는 원래 목표와 같다")
    void recommend_noConsumed_remainingEqualsFullTargets() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)));

        assertThat(response.remainingTargets().calorieTarget())
                .isEqualTo(response.targets().calorieTarget());
    }

    // ─── 헬퍼 ───

    private DailyDietRecommendationRequest request(List<MealType> mealTypes) {
        return new DailyDietRecommendationRequest(LocalDate.now(), mealTypes, false);
    }

    private DailyDietRecommendationRequest requestWithAlternatives(List<MealType> mealTypes, int alternativeCount) {
        return new DailyDietRecommendationRequest(LocalDate.now(), mealTypes, false, alternativeCount);
    }

    private User fullProfileUser() {
        return User.builder()
                .id(1L).email("a@b.com").displayName("test")
                .sex(User.Sex.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .heightCm(175.0).weightKg(75.0)
                .activityLevel(User.ActivityLevel.MODERATELY_ACTIVE)
                .build();
    }

    private List<DietRecommendationCandidate> diverseFoods() {
        return List.of(
                food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 250.0),
                food(2L, "현미밥", FoodCategory.GRAIN, 250.0),
                food(3L, "브로콜리", FoodCategory.VEGETABLE, 250.0),
                food(4L, "사과", FoodCategory.FRUIT, 250.0),
                food(5L, "두부", FoodCategory.PROTEIN_SOURCE, 250.0),
                food(6L, "고구마", FoodCategory.GRAIN, 250.0)
        );
    }

    private DietRecommendationCandidates candidateSet() {
        return new DietRecommendationCandidates(diverseFoods());
    }

    private DietRecommendationCandidate food(Long id, String name, FoodCategory category, double cal) {
        return new DietRecommendationCandidate(
                id,
                name,
                null,
                category,
                cal,
                20.0,
                10.0,
                5.0,
                0L,
                id,
                AllergenConfidenceLevel.UNKNOWN,
                null,
                true,
                List.of(),
                false
        );
    }

}
