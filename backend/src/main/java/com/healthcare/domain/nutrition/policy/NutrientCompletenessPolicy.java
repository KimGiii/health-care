package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.goals.entity.Goal;

/**
 * 목표 유형별 추천 후보 영양 완전성 정책.
 * 계획 §5.2: "모든 목표의 기본 후보에는 열량·단백질·탄수화물·지방·제공량이 필요하다."
 */
public class NutrientCompletenessPolicy {

    public boolean isComplete(Goal.GoalType goalType, DietRecommendationCandidate candidate) {
        return candidate.macroDataComplete();
    }
}
