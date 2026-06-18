package com.healthcare.domain.diet.recommendation.usecase;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidatePool;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidates;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse.NutrientSummary;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.engine.DietRecommendationEngine;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.nutrition.dto.ConsumedNutrients;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.service.NutritionCalculator;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyDietRecommendationUseCases {

    private static final double CALORIE_LOWER_BOUND_RATIO = 0.90;
    private static final double CALORIE_UPPER_BOUND_RATIO = 1.10;
    private static final double PROTEIN_LOWER_BOUND_RATIO = 0.90;
    private static final String DISCLAIMER =
            "이 추천은 영양 정보 기반의 참고용 제안입니다. " +
            "알러젠 정보는 완전하지 않을 수 있으니, 식품 라벨을 반드시 확인하세요.";

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final DietRestrictionRepository dietRestrictionRepository;
    private final DietLogRepository dietLogRepository;
    private final DietRecommendationCandidatePool candidatePool;
    private final DietRecommendationEngine engine;

    public DailyDietRecommendationResponse recommend(Long userId, DailyDietRecommendationRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("사용자를 찾을 수 없습니다."));

        if (!NutritionCalculator.canCalculate(user)) {
            throw new BusinessRuleViolationException(
                    "추천에 필요한 프로필 정보가 부족합니다. 성별/생년월일/키/체중/활동 수준을 모두 입력해 주세요.");
        }

        Goal.GoalType goalType = goalRepository.findActiveGoalByUserId(userId)
                .map(Goal::getGoalType)
                .orElse(null);
        NutritionTargets targets = NutritionCalculator.computeFor(user, goalType);

        List<DietRestriction> restrictions = dietRestrictionRepository
                .findByUserIdAndDeletedAtIsNull(userId);

        List<DietLog> todayLogs = dietLogRepository.findByUserIdAndLogDate(userId, request.date());
        ConsumedNutrients consumed = ConsumedNutrients.from(todayLogs);
        NutritionTargets remainingTargets = targets.minus(consumed);

        DietRecommendationCandidates candidates = candidatePool.load(restrictions, request.strictAllergyMode());
        if (candidates.foods().isEmpty()) {
            throw new BusinessRuleViolationException(
                    "등록한 제한 조건을 모두 반영하면 추천 가능한 식품 후보가 부족합니다. 제한 조건을 조정하거나 직접 식단을 기록해 주세요.");
        }

        List<RecommendedMeal> meals = engine.recommend(
                request.date(),
                remainingTargets,
                request.mealTypes(),
                candidates.foods());
        if (meals.stream().anyMatch(meal -> meal.items().isEmpty())) {
            throw new BusinessRuleViolationException(
                    "선택한 끼니 구성에 맞는 추천 식단을 만들기 어렵습니다. 끼니 수를 조정하거나 제한 조건을 줄여 주세요.");
        }

        NutrientSummary summary = buildSummary(meals);
        String failureReason = checkTargets(remainingTargets, summary);
        List<DietRestrictionResponse> appliedRestrictions = restrictions.stream()
                .map(DietRestrictionResponse::from)
                .toList();

        List<List<RecommendedMeal>> alternatives = request.alternativeCount() > 0
                ? engine.recommendAlternatives(request.alternativeCount(), request.date(),
                        remainingTargets, request.mealTypes(), candidates.foods())
                : List.of();

        return new DailyDietRecommendationResponse(
                request.date(),
                targets,
                remainingTargets,
                appliedRestrictions,
                meals,
                summary,
                failureReason,
                request.strictAllergyMode(),
                DISCLAIMER,
                alternatives
        );
    }

    private String checkTargets(NutritionTargets targets, NutrientSummary summary) {
        double lowerCalorie = targets.calorieTarget() * CALORIE_LOWER_BOUND_RATIO;
        double upperCalorie = targets.calorieTarget() * CALORIE_UPPER_BOUND_RATIO;
        if (summary.totalCalories() < lowerCalorie || summary.totalCalories() > upperCalorie) {
            return "현재 후보 식품만으로 하루 칼로리 목표에 맞는 추천 식단을 만들기 어렵습니다.";
        }

        double lowerProtein = targets.proteinTargetG() * PROTEIN_LOWER_BOUND_RATIO;
        if (summary.totalProteinG() < lowerProtein) {
            return "현재 후보 식품만으로 단백질 목표에 맞는 추천 식단을 만들기 어렵습니다.";
        }

        return null;
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
