package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("목표별 영양 정책")
class GoalAwareNutritionPolicyTest {

    private final GoalAwareNutritionPolicy policies = new GoalAwareNutritionPolicy();

    @Test
    @DisplayName("체중 감량은 불확실성 버퍼를 적용한 열량 상한과 단백질 하한을 지킨다")
    void weightLossCapsCaloriesAndFloorsProtein() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 150, 220, 60)
        );

        assertThat(policy.accepts(new NutritionVector(1960, 150, 200, 55))).isTrue();
        assertThat(policy.accepts(new NutritionVector(1961, 150, 200, 55))).isFalse();
        assertThat(policy.accepts(new NutritionVector(1900, 149, 200, 55))).isFalse();
    }

    @Test
    @DisplayName("근육량 증가는 열량·단백질 하한과 과도한 열량 초과 상한을 지킨다")
    void muscleGainFloorsCaloriesAndProteinAndCapsSurplus() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.MUSCLE_GAIN,
                new NutritionTargets(2500, 160, 320, 70)
        );

        assertThat(policy.accepts(new NutritionVector(2500, 160, 300, 70))).isTrue();
        assertThat(policy.accepts(new NutritionVector(2499, 160, 300, 70))).isFalse();
        assertThat(policy.accepts(new NutritionVector(2500, 159, 300, 70))).isFalse();
        assertThat(policy.accepts(new NutritionVector(2751, 160, 300, 70))).isFalse();
    }

    @Test
    @DisplayName("지구력 향상은 열량과 탄수화물 하한을 지킨다")
    void enduranceFloorsCaloriesAndCarbs() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.ENDURANCE,
                new NutritionTargets(2800, 120, 400, 75)
        );

        assertThat(policy.accepts(new NutritionVector(2800, 110, 400, 70))).isTrue();
        assertThat(policy.accepts(new NutritionVector(2799, 120, 400, 70))).isFalse();
        assertThat(policy.accepts(new NutritionVector(2800, 120, 399, 70))).isFalse();
    }

    @Test
    @DisplayName("건강 유지는 열량과 모든 매크로의 허용 범위를 지킨다")
    void generalHealthBoundsCaloriesAndMacros() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.GENERAL_HEALTH,
                new NutritionTargets(2000, 100, 250, 65)
        );

        assertThat(policy.accepts(new NutritionVector(2000, 100, 250, 65))).isTrue();
        assertThat(policy.accepts(new NutritionVector(1899, 100, 250, 65))).isFalse();
        assertThat(policy.accepts(new NutritionVector(2000, 89, 250, 65))).isFalse();
        assertThat(policy.accepts(new NutritionVector(2000, 100, 276, 65))).isFalse();
        assertThat(policy.accepts(new NutritionVector(2000, 100, 250, 72))).isFalse();
    }

    @Test
    @DisplayName("체형 개선은 체중 감량과 같은 열량 상한·단백질 하한 계약을 사용한다")
    void bodyRecompositionCapsCaloriesAndFloorsProtein() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.BODY_RECOMPOSITION,
                new NutritionTargets(2200, 140, 260, 65)
        );

        assertThat(policy.accepts(new NutritionVector(2156, 140, 240, 60))).isTrue();
        assertThat(policy.accepts(new NutritionVector(2157, 140, 240, 60))).isFalse();
        assertThat(policy.accepts(new NutritionVector(2100, 139, 240, 60))).isFalse();
    }

    @Test
    @DisplayName("활성 목표가 없으면 현재 버전의 건강 유지 정책을 사용한다")
    void missingGoalUsesVersionedGeneralHealthPolicy() {
        NutritionPolicy policy = policies.resolve(
                null,
                new NutritionTargets(2000, 100, 250, 65)
        );

        assertThat(policy.goalType()).isEqualTo(Goal.GoalType.GENERAL_HEALTH);
        assertThat(policy.version()).isEqualTo(GoalAwareNutritionPolicy.CURRENT_VERSION);
        assertThat(policy.accepts(new NutritionVector(2000, 100, 250, 65))).isTrue();
    }

    @Test
    @DisplayName("정책 평가는 위반한 영양 상·하한 원인을 반환한다")
    void reportsConstraintViolationReasons() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.MUSCLE_GAIN,
                new NutritionTargets(2500, 160, 320, 70)
        );

        assertThat(policy.violations(new NutritionVector(2499, 159, 320, 70)))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        NutritionPolicyViolation.CALORIES_BELOW_MINIMUM,
                        NutritionPolicyViolation.PROTEIN_BELOW_MINIMUM
                ));
    }
}
