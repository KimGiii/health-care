package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.policy.NutritionVector;

import java.util.List;

/**
 * 추천 가능성(feasibility) sweep의 한 칸. 실제 사용자 조합(목표 × 끼니 선택 × 후보 풀)을 모사해
 * 엔진이 feasible 해를 내는지, 못 내면 어떤 구조화 사유로 막히는지 측정한다.
 *
 * <p>합성 풀로 결정적으로 돌릴 수도, 운영 풀 스냅샷을 주입할 수도 있다. confirmedIntake는 이미 기록한
 * 끼니(아침·점심 등)를 차감하기 위한 확정 섭취다. 끼니 부분 선택(예: 저녁+간식)에서 남은 목표가 어떻게
 * 분배되는지가 핵심 관찰 대상이다.
 */
record FeasibilitySweepScenario(
        String id,
        Goal.GoalType goalType,
        NutritionTargets targets,
        List<MealType> mealTypes,
        List<DietRecommendationCandidate> pool,
        NutritionVector confirmedIntake
) {
}
