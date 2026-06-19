package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.nutrition.dto.NutritionTargets;

import java.time.LocalDate;
import java.util.List;

public record DailyDietRecommendationResponse(
        LocalDate date,
        NutritionTargets targets,
        NutritionTargets remainingTargets,
        List<DietRestrictionResponse> appliedRestrictions,
        List<RecommendedMeal> meals,
        NutrientSummary totalNutrients,
        String failureReason,
        boolean strictAllergyMode,
        String disclaimer,
        List<List<RecommendedMeal>> alternatives,
        Long snapshotId
) {
    public static final String DISCLAIMER =
            "이 추천은 영양 정보 기반의 참고용 제안입니다. " +
            "알러젠 정보는 완전하지 않을 수 있으니, 식품 라벨을 반드시 확인하세요.";

    /** 정상 추천 응답. disclaimer는 고정 문구를 채운다. */
    public static DailyDietRecommendationResponse of(
            LocalDate date,
            NutritionTargets targets,
            NutritionTargets remainingTargets,
            List<DietRestrictionResponse> appliedRestrictions,
            List<RecommendedMeal> meals,
            NutrientSummary totalNutrients,
            String failureReason,
            boolean strictAllergyMode,
            List<List<RecommendedMeal>> alternatives,
            Long snapshotId
    ) {
        return new DailyDietRecommendationResponse(
                date, targets, remainingTargets, appliedRestrictions, meals,
                totalNutrients, failureReason, strictAllergyMode, DISCLAIMER,
                alternatives, snapshotId);
    }

    /** 이미 목표를 달성해 추천이 불필요한 경우의 응답(끼니·대안 없음, 스냅샷 없음). */
    public static DailyDietRecommendationResponse alreadyMet(
            LocalDate date,
            NutritionTargets targets,
            NutritionTargets remainingTargets,
            List<DietRestrictionResponse> appliedRestrictions,
            boolean strictAllergyMode,
            String reason
    ) {
        return new DailyDietRecommendationResponse(
                date, targets, remainingTargets, appliedRestrictions,
                List.of(), new NutrientSummary(0, 0, 0, 0), reason,
                strictAllergyMode, DISCLAIMER, List.of(), null);
    }

    public boolean succeeded() {
        return failureReason == null;
    }

    public record NutrientSummary(
            double totalCalories,
            double totalProteinG,
            double totalCarbsG,
            double totalFatG
    ) {}
}
