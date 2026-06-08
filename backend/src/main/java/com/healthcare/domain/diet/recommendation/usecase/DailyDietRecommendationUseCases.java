package com.healthcare.domain.diet.recommendation.usecase;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse.NutrientSummary;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.diet.recommendation.engine.DietRecommendationEngine;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.service.NutritionCalculator;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyDietRecommendationUseCases {

    private static final String DISCLAIMER =
            "이 추천은 영양 정보 기반의 참고용 제안입니다. " +
            "알러젠 정보는 완전하지 않을 수 있으니, 식품 라벨을 반드시 확인하세요.";

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final DietRestrictionRepository dietRestrictionRepository;
    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodAllergenTagRepository foodAllergenTagRepository;
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

        List<FoodCatalog> allCandidates = foodCatalogRepository.findAll();
        List<Long> foodIds = allCandidates.stream().map(FoodCatalog::getId).toList();

        Map<Long, List<FoodAllergenTag>> tagsByFoodId = foodAllergenTagRepository
                .findByFoodCatalogIdIn(foodIds)
                .stream()
                .collect(Collectors.groupingBy(FoodAllergenTag::getFoodCatalogId));

        List<FoodCatalog> filtered = engine.filterCandidates(
                allCandidates, restrictions, tagsByFoodId, request.strictAllergyMode());

        List<RecommendedMeal> meals = engine.recommend(
                targets, request.mealTypes(), filtered, tagsByFoodId, request.strictAllergyMode());

        NutrientSummary summary = buildSummary(meals);
        List<DietRestrictionResponse> appliedRestrictions = restrictions.stream()
                .map(DietRestrictionResponse::from)
                .toList();

        return new DailyDietRecommendationResponse(
                request.date(),
                targets,
                appliedRestrictions,
                meals,
                summary,
                request.strictAllergyMode(),
                DISCLAIMER
        );
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
