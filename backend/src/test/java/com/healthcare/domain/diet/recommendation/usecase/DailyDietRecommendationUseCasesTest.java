package com.healthcare.domain.diet.recommendation.usecase;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption.ServingType;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidatePool;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidates;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.engine.ConstraintRecommendationEngine;
import com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason;
import com.healthcare.domain.diet.recommendation.engine.RecommendationRationale;
import com.healthcare.domain.diet.recommendation.engine.RecommendationResult;
import com.healthcare.domain.diet.recommendation.engine.RecommendationSolution;
import com.healthcare.domain.diet.recommendation.snapshot.OnlinePreferenceSignalLoader;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationSnapshotStore;
import com.healthcare.domain.diet.repository.FoodEntryRepository;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyDietRecommendationUseCases 단위 테스트")
class DailyDietRecommendationUseCasesTest {

    @Mock private UserRepository userRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private DietRestrictionRepository dietRestrictionRepository;
    @Mock private DietLogRepository dietLogRepository;
    @Mock private DietRecommendationCandidatePool candidatePool;
    @Mock private RecommendationSnapshotStore snapshotStore;
    @Mock private FoodEntryRepository foodEntryRepository;
    @Mock private ConstraintRecommendationEngine engine;
    @Mock private OnlinePreferenceSignalLoader onlinePreferenceSignalLoader;

    @InjectMocks
    private DailyDietRecommendationUseCases useCases;

    @Test
    @DisplayName("recommend는 스냅샷·이벤트를 저장하므로 쓰기 트랜잭션 경계를 가진다")
    void recommend_hasWritableTransaction() throws NoSuchMethodException {
        Transactional annotation = DailyDietRecommendationUseCases.class
                .getMethod("recommend", Long.class, DailyDietRecommendationRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(annotation)
                .as("recommend는 메서드 레벨 @Transactional로 클래스 기본 readOnly를 오버라이드해야 한다")
                .isNotNull();
        assertThat(annotation.readOnly())
                .as("스냅샷 INSERT가 read-only 트랜잭션에서 실패하지 않도록 readOnly=false 여야 한다")
                .isFalse();
    }

    @Test
    @DisplayName("필수 프로필 정보가 없으면 TARGET_PROFILE_MISSING 실패 응답을 반환한다")
    void recommend_incompleteProfile_returnsStructuredFailure() {
        User user = User.builder().id(1L).email("a@b.com").displayName("test").build(); // 프로필 미완
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH)));

        assertThat(response.succeeded()).isFalse();
        assertThat(response.failureCode())
                .isEqualTo(RecommendationFailureReason.TARGET_PROFILE_MISSING.name());
        assertThat(response.meals()).isEmpty();
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
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 0);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)));

        assertThat(response).isNotNull();
        assertThat(response.meals()).hasSize(3);
        assertThat(response.appliedRestrictions()).isEmpty();
        assertThat(response.rationale()).isNotNull();
    }

    @Test
    @DisplayName("추천 후보 풀 Module에서 후보를 로드한다")
    void recommend_loadsCandidatesFromCandidatePool() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.BREAKFAST), 0);

        useCases.recommend(1L, request(List.of(MealType.BREAKFAST)));

        verify(candidatePool).load(List.of(), false);
    }

    @Test
    @DisplayName("알러젠 제한이 있으면 응답에 적용된 제한 조건이 포함된다")
    void recommend_withRestrictions_appliedRestrictionsInResponse() {
        DietRestriction restriction = DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(RestrictionType.ALLERGY)
                .targetType(TargetType.ALLERGEN_TAG).allergenTag(AllergenTag.MILK)
                .build();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(fullProfileUser()));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of(restriction));
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.LUNCH), 0);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.LUNCH)));

        assertThat(response.appliedRestrictions()).hasSize(1);
    }

    @Test
    @DisplayName("제한 적용 후 추천 후보가 없으면 ALLERGEN_VERIFIED_POOL_INSUFFICIENT 실패 응답을 반환한다")
    void recommend_noCandidates_returnsStructuredFailure() {
        stubCommon();
        // 알러젠 게이트에서 모두 탈락 → 통과 0, 지배 원인 ALLERGEN
        given(candidatePool.load(any(), anyBoolean())).willReturn(new DietRecommendationCandidates(
                List.of(),
                new com.healthcare.domain.diet.recommendation.candidate.CandidatePoolDiagnostics(
                        0, 0, 0, 0, 5)));

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST)));

        assertThat(response.succeeded()).isFalse();
        assertThat(response.failureCode())
                .isEqualTo(RecommendationFailureReason.ALLERGEN_VERIFIED_POOL_INSUFFICIENT.name());
    }

    @Test
    @DisplayName("엔진이 구조화된 실패를 반환하면 failureCode·failureReason 응답을 반환한다")
    void recommend_engineFailure_returnsFailureReason() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        given(engine.recommend(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(RecommendationResult.failure(
                        RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE));

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST)));

        assertThat(response.succeeded()).isFalse();
        assertThat(response.failureReason()).isNotBlank();
        assertThat(response.failureCode())
                .isEqualTo(RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE.name());
    }

    @Test
    @DisplayName("알러젠 제한 후보가 단백질 하한에 물리적으로 못 닿으면 알러젠 검증 풀 부족으로 분류한다")
    void recommend_engineInfeasibleWithAllergenProteinShortfall_returnsAllergenPoolInsufficient() {
        DietRestriction milkRestriction = DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(RestrictionType.ALLERGY)
                .targetType(TargetType.ALLERGEN_TAG).allergenTag(AllergenTag.MILK)
                .build();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(fullProfileUser()));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of(milkRestriction));
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
        given(candidatePool.load(any(), anyBoolean())).willReturn(new DietRecommendationCandidates(List.of(
                foodWithServing(1L, "저단백음료A", FoodCategory.BEVERAGE, 100.0, 1.0, 100.0),
                foodWithServing(2L, "저단백음료B", FoodCategory.BEVERAGE, 100.0, 1.0, 100.0)
        )));
        given(engine.recommend(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(RecommendationResult.failure(
                        RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE));

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.DINNER, MealType.SNACK)));

        assertThat(response.succeeded()).isFalse();
        assertThat(response.failureCode())
                .isEqualTo(RecommendationFailureReason.ALLERGEN_VERIFIED_POOL_INSUFFICIENT.name());
    }

    @Test
    @DisplayName("당일 이미 섭취한 영양량이 있으면 응답에 남은 목표가 반영된다")
    void recommend_withConsumedNutrients_remainingTargetsReflected() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(fullProfileUser()));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());

        DietLog breakfastLog = DietLog.builder()
                .userId(1L).logDate(LocalDate.now()).mealType(MealType.BREAKFAST)
                .totalCalories(500.0).totalProteinG(30.0).totalCarbsG(60.0).totalFatG(15.0)
                .build();
        given(dietLogRepository.findByUserIdAndLogDate(1L, LocalDate.now()))
                .willReturn(List.of(breakfastLog));
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.LUNCH, MealType.DINNER), 0);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.LUNCH, MealType.DINNER)));

        assertThat(response.remainingTargets()).isNotNull();
        assertThat(response.remainingTargets().calorieTarget())
                .isLessThan(response.targets().calorieTarget());
    }

    @Test
    @DisplayName("당일 칼로리 목표를 이미 달성했으면 추천 없이 달성 안내 failureReason을 반환한다")
    void recommend_caloriesAlreadyMet_returnsGoalAchievedFailureReason() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(fullProfileUser()));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());

        DietLog heavyLog = DietLog.builder()
                .userId(1L).logDate(LocalDate.now()).mealType(MealType.LUNCH)
                .totalCalories(3000.0).totalProteinG(200.0).totalCarbsG(350.0).totalFatG(100.0)
                .build();
        given(dietLogRepository.findByUserIdAndLogDate(1L, LocalDate.now()))
                .willReturn(List.of(heavyLog));

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.DINNER)));

        assertThat(response.succeeded()).isFalse();
        assertThat(response.failureReason()).contains("달성");
        assertThat(response.meals()).isEmpty();
    }

    @Test
    @DisplayName("남은 목표가 있으면 정상 추천 응답을 반환한다")
    void recommend_goalsPartiallyMet_stillRecommends() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(fullProfileUser()));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());

        DietLog halfLog = DietLog.builder()
                .userId(1L).logDate(LocalDate.now()).mealType(MealType.BREAKFAST)
                .totalCalories(800.0).totalProteinG(50.0).totalCarbsG(100.0).totalFatG(25.0)
                .build();
        given(dietLogRepository.findByUserIdAndLogDate(1L, LocalDate.now()))
                .willReturn(List.of(halfLog));
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.LUNCH, MealType.DINNER), 0);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.LUNCH, MealType.DINNER)));

        assertThat(response.meals()).hasSize(2);
        assertThat(response.remainingTargets().calorieTarget()).isGreaterThan(0);
    }

    @Test
    @DisplayName("alternativeCount=2 요청 시 응답에 2개의 대안 식단이 포함된다")
    void recommend_alternativeCount2_responseContainsTwoAlternatives() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.LUNCH), 2);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                requestWithAlternatives(List.of(MealType.LUNCH), 2));

        assertThat(response.alternatives()).hasSize(2);
    }

    @Test
    @DisplayName("엔진이 요청보다 적은 대안을 반환하면 실제 생성된 수만큼 반환된다")
    void recommend_fewerAlternatives_returnsActualCount() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.LUNCH), 1); // primary + 1 대안만

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                requestWithAlternatives(List.of(MealType.LUNCH), 5));

        assertThat(response.alternatives()).hasSizeLessThan(5);
        assertThat(response.alternatives()).isNotEmpty();
    }

    @Test
    @DisplayName("alternativeCount=0(기본값) 요청 시 alternatives는 빈 리스트다")
    void recommend_defaultRequest_alternativesEmpty() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 0);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)));

        assertThat(response.alternatives()).isEmpty();
    }

    @Test
    @DisplayName("당일 섭취 기록이 없으면 남은 목표는 원래 목표와 같다")
    void recommend_noConsumed_remainingEqualsFullTargets() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 0);

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)));

        assertThat(response.remainingTargets().calorieTarget())
                .isEqualTo(response.targets().calorieTarget());
    }

    // ─── 헬퍼 ───

    private void stubCommon() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(fullProfileUser()));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(dietLogRepository.findByUserIdAndLogDate(any(), any())).willReturn(List.of());
    }

    @Test
    @DisplayName("alternativeCount 미지정이어도 기본 버퍼만큼 후보를 미리 요청한다(다시 추천 즉시성)")
    void recommend_defaultBuffer_prefetchesAlternatives() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.BREAKFAST), 0);

        useCases.recommend(1L, request(List.of(MealType.BREAKFAST)));

        ArgumentCaptor<Integer> maxSolutions = ArgumentCaptor.forClass(Integer.class);
        verify(engine).recommend(any(), any(), any(), any(), any(), any(), maxSolutions.capture(), any());
        // primary 1 + 기본 버퍼 4 = 5
        assertThat(maxSolutions.getValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("요청 alternativeCount가 기본 버퍼보다 크면 요청값을 따른다")
    void recommend_largerRequestedCount_honored() {
        stubCommon();
        given(candidatePool.load(any(), anyBoolean())).willReturn(candidateSet());
        givenEngineSucceeds(List.of(MealType.BREAKFAST), 0);

        useCases.recommend(1L, requestWithAlternatives(List.of(MealType.BREAKFAST), 8));

        ArgumentCaptor<Integer> maxSolutions = ArgumentCaptor.forClass(Integer.class);
        verify(engine).recommend(any(), any(), any(), any(), any(), any(), maxSolutions.capture(), any());
        assertThat(maxSolutions.getValue()).isEqualTo(9); // 1 + max(8, 4)
    }

    private void givenEngineSucceeds(List<MealType> mealTypes, int alternativeCount) {
        given(engine.recommend(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .willReturn(success(mealTypes, alternativeCount));
    }

    private RecommendationResult success(List<MealType> mealTypes, int alternativeCount) {
        List<RecommendationSolution> solutions = new ArrayList<>();
        for (int s = 0; s <= alternativeCount; s++) {
            solutions.add(solution(mealTypes));
        }
        return RecommendationResult.success(solutions);
    }

    private RecommendationSolution solution(List<MealType> mealTypes) {
        List<RecommendedMeal> meals = mealTypes.stream().map(this::meal).toList();
        RecommendationRationale rationale = new RecommendationRationale(
                500, 40, 60, 100.0, 5.0, "2026-06-18.v1", "테스트 근거");
        return new RecommendationSolution(meals, rationale);
    }

    private RecommendedMeal meal(MealType type) {
        RecommendedFoodEntry item = new RecommendedFoodEntry(
                1L, "food", null, FoodCategory.GRAIN, 100, 250, 20, 10, 5,
                AllergenConfidenceLevel.UNKNOWN, null);
        return new RecommendedMeal(type, 250, 250, 20, 10, 5, List.of(item));
    }

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

    private DietRecommendationCandidates candidateSet() {
        return new DietRecommendationCandidates(List.of(
                food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 250.0),
                food(2L, "현미밥", FoodCategory.GRAIN, 250.0),
                food(3L, "브로콜리", FoodCategory.VEGETABLE, 250.0),
                food(4L, "사과", FoodCategory.FRUIT, 250.0),
                food(5L, "두부", FoodCategory.PROTEIN_SOURCE, 250.0),
                food(6L, "고구마", FoodCategory.GRAIN, 250.0)
        ));
    }

    private DietRecommendationCandidate food(Long id, String name, FoodCategory category, double cal) {
        return new DietRecommendationCandidate(
                id, name, null, category, cal, 20.0, 10.0, 5.0, 0L, id,
                AllergenConfidenceLevel.UNKNOWN, null, true, List.of(), true);
    }

    private DietRecommendationCandidate foodWithServing(
            Long id, String name, FoodCategory category,
            double caloriesPer100g, double proteinPer100g, double servingG) {
        ServingOptionSnapshot serving = new ServingOptionSnapshot(
                "1회 제공량", "1회 제공량", servingG, 0, ServingType.OFFICIAL_SERVING, true);
        return new DietRecommendationCandidate(
                id, name, null, category, caloriesPer100g, proteinPer100g, 10.0, 1.0, 0L, id,
                AllergenConfidenceLevel.LABEL_DERIVED, null, true, List.of(serving), true);
    }
}
