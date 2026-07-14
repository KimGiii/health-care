package com.healthcare.domain.diet.recommendation.usecase;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.candidate.CandidatePoolDiagnostics;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidatePool;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidates;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse.NutrientSummary;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.engine.ConstraintRecommendationEngine;
import com.healthcare.domain.diet.recommendation.snapshot.OnlinePreferenceSignalLoader;
import com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason;
import com.healthcare.domain.diet.recommendation.engine.RecommendationResult;
import com.healthcare.domain.diet.recommendation.engine.RecommendationSolution;
import com.healthcare.domain.diet.recommendation.engine.UserRepetitionPolicy;
import com.healthcare.domain.diet.recommendation.snapshot.RecommendationSnapshotStore;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.repository.FoodEntryRepository;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.nutrition.dto.ConsumedNutrients;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.policy.GoalAwareNutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionEstimate;
import com.healthcare.domain.nutrition.policy.NutritionPolicy;
import com.healthcare.domain.nutrition.policy.NutritionVector;
import com.healthcare.domain.nutrition.policy.RemainingNutritionBudget;
import com.healthcare.domain.nutrition.policy.RemainingNutritionCalculator;
import com.healthcare.domain.nutrition.service.NutritionCalculator;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyDietRecommendationUseCases {

    private static final int REPETITION_LOOKBACK_DAYS = 7;
    private static final int MAX_ITEMS_PER_MAIN_MEAL = 3;
    private static final int MAX_ITEMS_PER_SNACK = 2;
    /**
     * 요청이 대안 수를 지정하지 않아도 미리 생성하는 기본 후보 버퍼. iOS가 버퍼에서 즉시 "다시 추천"하고
     * 소진 시에만 재호출하도록 한다(ETM num_results 패턴). 작은 검증 풀을 고려한 가설값 — benchmark·제품으로 조정.
     */
    private static final int DEFAULT_ALTERNATIVE_BUFFER = 4;
    private static final NutritionTargets ZERO_TARGETS = new NutritionTargets(0, 0, 0, 0);

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final DietRestrictionRepository dietRestrictionRepository;
    private final DietLogRepository dietLogRepository;
    private final DietRecommendationCandidatePool candidatePool;
    private final ConstraintRecommendationEngine engine;
    private final RecommendationSnapshotStore snapshotStore;
    private final FoodEntryRepository foodEntryRepository;
    private final OnlinePreferenceSignalLoader onlinePreferenceSignalLoader;

    // 무상태 정책 객체 — 빈 등록 없이 직접 보유한다.
    private final GoalAwareNutritionPolicy policies = new GoalAwareNutritionPolicy();
    private final RemainingNutritionCalculator remainingCalculator = new RemainingNutritionCalculator();

    // 추천 생성 시 스냅샷·이벤트를 저장하므로 클래스 기본 readOnly 트랜잭션을 쓰기 트랜잭션으로 오버라이드한다.
    @Transactional
    public DailyDietRecommendationResponse recommend(Long userId, DailyDietRecommendationRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("사용자를 찾을 수 없습니다."));

        List<DietRestriction> restrictions = dietRestrictionRepository
                .findByUserIdAndDeletedAtIsNull(userId);
        List<DietRestrictionResponse> appliedRestrictions = restrictions.stream()
                .map(DietRestrictionResponse::from)
                .toList();

        if (!NutritionCalculator.canCalculate(user)) {
            return failureResponse(request, ZERO_TARGETS, ZERO_TARGETS, appliedRestrictions,
                    RecommendationFailureReason.TARGET_PROFILE_MISSING);
        }

        Goal.GoalType goalType = goalRepository.findActiveGoalByUserId(userId)
                .map(Goal::getGoalType)
                .orElse(null);
        NutritionTargets targets = NutritionCalculator.computeFor(user, goalType);

        List<DietLog> todayLogs = dietLogRepository.findByUserIdAndLogDate(userId, request.date());
        ConsumedNutrients consumed = ConsumedNutrients.from(todayLogs);
        NutritionTargets remainingTargets = targets.minus(consumed);

        if (remainingTargets.calorieTarget() <= 0) {
            return DailyDietRecommendationResponse.alreadyMet(
                    request.date(), targets, remainingTargets, appliedRestrictions,
                    request.strictAllergyMode(),
                    "오늘 칼로리 목표를 이미 달성했습니다. 추가 추천이 필요하지 않습니다.");
        }

        DietRecommendationCandidates candidates = candidatePool.load(restrictions, request.strictAllergyMode());
        // 추천 후보는 영양 완전성과 검증된 1회 제공량 옵션을 모두 갖춰야 한다(§7.1).
        // 제공량 근거가 없는 식품은 비현실적 분량으로 이어지므로 추천에서 제외한다.
        List<DietRecommendationCandidate> eligible = candidates.foods().stream()
                .filter(DietRecommendationCandidate::macroDataComplete)
                .filter(DietRecommendationCandidate::hasVerifiedServingOptions)
                .toList();
        if (eligible.isEmpty()) {
            return failureResponse(request, targets, remainingTargets, appliedRestrictions,
                    classifyEmptyEligible(candidates));
        }

        // 정책은 전체 일일 목표에 적용한 뒤 확정 섭취를 차감한다(리뷰 P0-1).
        NutritionPolicy policy = policies.resolve(goalType, targets);
        NutritionVector consumedVector = new NutritionVector(
                consumed.calories(), consumed.proteinG(), consumed.carbsG(), consumed.fatG());
        RemainingNutritionBudget budget = remainingCalculator.calculate(
                policy, List.of(NutritionEstimate.confirmed(consumedVector)));

        Set<Long> recentIds = foodEntryRepository.findRecentFoodCatalogIds(
                userId, request.date().minusDays(REPETITION_LOOKBACK_DAYS));
        UserRepetitionPolicy repetitionPolicy = new UserRepetitionPolicy(recentIds);

        // 요청값과 기본 버퍼 중 큰 쪽만큼 후보를 미리 생성한다(다시 추천 즉시성).
        int maxSolutions = 1 + Math.max(request.alternativeCount(), DEFAULT_ALTERNATIVE_BUFFER);
        // R1: 본인 최근 30일 참여 신호(기록 전환 우대·음식 기인 거절 불이익)를 soft 순위에 반영.
        RecommendationResult result = engine.recommend(
                request.date(), budget, request.mealTypes(), eligible,
                repetitionPolicy, onlinePreferenceSignalLoader.load(userId),
                maxSolutions, GoalAwareNutritionPolicy.CURRENT_VERSION);

        if (result instanceof RecommendationResult.Failure failure) {
            RecommendationFailureReason reason = classifyEngineFailure(
                    failure.reason(), restrictions, request.mealTypes(), eligible, budget);
            return failureResponse(request, targets, remainingTargets, appliedRestrictions,
                    reason);
        }

        RecommendationResult.Success success = (RecommendationResult.Success) result;
        RecommendationSolution primary = success.primary();
        List<RecommendedMeal> meals = primary.meals();
        NutrientSummary summary = buildSummary(meals);

        Long snapshotId = snapshotStore.save(userId, request.date(), meals, goalType,
                request.strictAllergyMode());

        return DailyDietRecommendationResponse.of(
                request.date(), targets, remainingTargets, appliedRestrictions, meals,
                summary, null, primary.rationale(), request.strictAllergyMode(),
                success.alternatives(), snapshotId);
    }

    /** 통과 후보가 비었을 때 후보 풀 진단으로 실패 사유를 판정한다(리뷰 P0-3). */
    private RecommendationFailureReason classifyEmptyEligible(DietRecommendationCandidates candidates) {
        // 통과 후보는 있으나 전부 macro 결측 → 데이터 결측
        if (!candidates.foods().isEmpty()) {
            return RecommendationFailureReason.NUTRIENT_DATA_INCOMPLETE;
        }
        CandidatePoolDiagnostics.DominantDropCause cause = candidates.diagnostics().dominantDropCause();
        if (cause == null) {
            return RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE;
        }
        return switch (cause) {
            case ALLERGEN -> RecommendationFailureReason.ALLERGEN_VERIFIED_POOL_INSUFFICIENT;
            case INCOMPLETE_MACRO -> RecommendationFailureReason.NUTRIENT_DATA_INCOMPLETE;
            case RESTRICTION, FRESHNESS, NONE -> RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE;
        };
    }

    private RecommendationFailureReason classifyEngineFailure(
            RecommendationFailureReason reason,
            List<DietRestriction> restrictions,
            List<MealType> selectedMeals,
            List<DietRecommendationCandidate> eligible,
            RemainingNutritionBudget budget
    ) {
        if (reason != RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE
                || !hasAllergenRestriction(restrictions)
                || canReachProteinFloorWithinSelectedMealSlots(selectedMeals, eligible, budget)) {
            return reason;
        }
        return RecommendationFailureReason.ALLERGEN_VERIFIED_POOL_INSUFFICIENT;
    }

    private boolean hasAllergenRestriction(List<DietRestriction> restrictions) {
        return restrictions.stream()
                .anyMatch(restriction -> restriction.getTargetType() == DietRestriction.TargetType.ALLERGEN_TAG
                        && restriction.getAllergenTag() != null);
    }

    private boolean canReachProteinFloorWithinSelectedMealSlots(
            List<MealType> selectedMeals,
            List<DietRecommendationCandidate> eligible,
            RemainingNutritionBudget budget
    ) {
        double proteinFloor = budget.protein().minimum();
        if (!Double.isFinite(proteinFloor) || proteinFloor <= 0.0) {
            return true;
        }
        int maxItems = selectedMeals.stream()
                .mapToInt(meal -> meal == MealType.SNACK ? MAX_ITEMS_PER_SNACK : MAX_ITEMS_PER_MAIN_MEAL)
                .sum();
        double theoreticalMaxProtein = eligible.stream()
                .mapToDouble(this::maxProteinFromVerifiedServingOptions)
                .boxed()
                .sorted(java.util.Comparator.reverseOrder())
                .limit(maxItems)
                .mapToDouble(Double::doubleValue)
                .sum();
        return theoreticalMaxProtein + 0.1 >= proteinFloor;
    }

    private double maxProteinFromVerifiedServingOptions(DietRecommendationCandidate candidate) {
        return candidate.verifiedServingGramOptions().stream()
                .mapToDouble(servingG -> candidate.proteinPer100g() * servingG / 100.0)
                .max()
                .orElse(0.0);
    }

    private DailyDietRecommendationResponse failureResponse(
            DailyDietRecommendationRequest request,
            NutritionTargets targets,
            NutritionTargets remainingTargets,
            List<DietRestrictionResponse> appliedRestrictions,
            RecommendationFailureReason reason
    ) {
        return DailyDietRecommendationResponse.failure(
                request.date(), targets, remainingTargets, appliedRestrictions,
                request.strictAllergyMode(), reason.name(), reason.userMessage());
    }

    private NutrientSummary buildSummary(List<RecommendedMeal> meals) {
        double cal = meals.stream().mapToDouble(RecommendedMeal::totalCalories).sum();
        double protein = meals.stream().mapToDouble(RecommendedMeal::totalProteinG).sum();
        double carbs = meals.stream().mapToDouble(RecommendedMeal::totalCarbsG).sum();
        double fat = meals.stream().mapToDouble(RecommendedMeal::totalFatG).sum();
        return new NutrientSummary(round(cal), round(protein), round(carbs), round(fat));
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
