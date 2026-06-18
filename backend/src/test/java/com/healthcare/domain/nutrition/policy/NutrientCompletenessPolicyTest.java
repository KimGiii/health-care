package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.goals.entity.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("목표별 영양 완전성 정책")
class NutrientCompletenessPolicyTest {

    private final NutrientCompletenessPolicy policy = new NutrientCompletenessPolicy();

    @ParameterizedTest(name = "{0}: 4종 매크로가 완전한 후보는 통과한다")
    @EnumSource(Goal.GoalType.class)
    @DisplayName("모든 목표 유형에서 칼로리·단백질·탄수·지방 4종이 완전한 후보는 완전성 검사를 통과한다")
    void allGoalTypes_completeMacros_passes(Goal.GoalType goalType) {
        DietRecommendationCandidate complete = candidate(200.0, 20.0, 25.0, 6.0, true);

        assertThat(policy.isComplete(goalType, complete)).isTrue();
    }

    @ParameterizedTest(name = "{0}: 매크로 결측 후보는 차단된다")
    @EnumSource(Goal.GoalType.class)
    @DisplayName("모든 목표 유형에서 매크로 데이터가 결측된 후보는 차단된다")
    void allGoalTypes_incompleteMacros_blocks(Goal.GoalType goalType) {
        DietRecommendationCandidate incomplete = candidate(200.0, 0.0, 0.0, 0.0, false);

        assertThat(policy.isComplete(goalType, incomplete)).isFalse();
    }

    private DietRecommendationCandidate candidate(
            double calories, double protein, double carbs, double fat, boolean macroDataComplete
    ) {
        return new DietRecommendationCandidate(
                1L, "테스트식품", null,
                FoodCategory.PROTEIN_SOURCE,
                calories, protein, carbs, fat,
                0L, 1L,
                AllergenConfidenceLevel.DIRECT_VERIFIED,
                null,
                macroDataComplete
        );
    }
}
