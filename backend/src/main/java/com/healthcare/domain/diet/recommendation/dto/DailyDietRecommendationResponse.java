package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.recommendation.engine.RecommendationRationale;
import com.healthcare.domain.diet.recommendation.engine.RecommendationSolution;
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
        /** 구조화된 실패 사유 코드(§8.3). 성공·이미달성 시 null. */
        String failureCode,
        /** 추천 근거(§10). primary 해 기준. 실패 시 null. */
        RecommendationRationale rationale,
        boolean strictAllergyMode,
        String disclaimer,
        /** 대안 해 — 각자 끼니 구성과 근거를 함께 보유한다(리뷰 P1-5). */
        List<RecommendationSolution> alternatives,
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
            RecommendationRationale rationale,
            boolean strictAllergyMode,
            List<RecommendationSolution> alternatives,
            Long snapshotId
    ) {
        return new DailyDietRecommendationResponse(
                date, targets, remainingTargets, appliedRestrictions, meals,
                totalNutrients, failureReason, null, rationale, strictAllergyMode, DISCLAIMER,
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
                List.of(), new NutrientSummary(0, 0, 0, 0), reason, null, null,
                strictAllergyMode, DISCLAIMER, List.of(), null);
    }

    /** 구조화된 실패 응답(§8.3). 끼니·대안·근거·스냅샷 없음. */
    public static DailyDietRecommendationResponse failure(
            LocalDate date,
            NutritionTargets targets,
            NutritionTargets remainingTargets,
            List<DietRestrictionResponse> appliedRestrictions,
            boolean strictAllergyMode,
            String failureCode,
            String failureReason
    ) {
        return new DailyDietRecommendationResponse(
                date, targets, remainingTargets, appliedRestrictions,
                List.of(), new NutrientSummary(0, 0, 0, 0), failureReason, failureCode, null,
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
