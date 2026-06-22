package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("남은 영양량 계산")
class RemainingNutritionCalculatorTest {

    private final GoalAwareNutritionPolicy policies = new GoalAwareNutritionPolicy();
    private final RemainingNutritionCalculator calculator = new RemainingNutritionCalculator();

    @Test
    @DisplayName("확정 섭취 기록을 목표별 hard constraint에서 차감한다")
    void subtractsConfirmedIntakeFromPolicy() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 150, 220, 60)
        );

        RemainingNutritionBudget remaining = calculator.calculate(
                policy,
                List.of(NutritionEstimate.confirmed(new NutritionVector(600, 40, 70, 20)))
        );

        assertThat(remaining.calories().maximum()).isEqualTo(1360.0);
        assertThat(remaining.protein().minimum()).isEqualTo(110.0);
        assertThat(remaining.feasible()).isTrue();
    }

    @Test
    @DisplayName("추정 섭취는 열량 상한과 단백질 하한을 보수적으로 차감한다")
    void subtractsEstimatedIntakeConservatively() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 150, 220, 60)
        );

        RemainingNutritionBudget remaining = calculator.calculate(
                policy,
                List.of(
                        NutritionEstimate.confirmed(new NutritionVector(600, 40, 70, 20)),
                        new NutritionEstimate(
                                new NutritionVector(400, 20, 45, 12),
                                new NutritionVector(550, 35, 65, 20)
                        )
                )
        );

        assertThat(remaining.calories().maximum()).isEqualTo(810.0);
        assertThat(remaining.protein().minimum()).isEqualTo(90.0);
        assertThat(remaining.feasible()).isTrue();
    }

    @Test
    @DisplayName("이미 열량 상한을 초과한 날은 남은 추천을 불가능으로 판정한다")
    void marksBudgetInfeasibleWhenCalorieCapIsAlreadyExceeded() {
        NutritionPolicy policy = policies.resolve(
                Goal.GoalType.WEIGHT_LOSS,
                new NutritionTargets(2000, 150, 220, 60)
        );

        RemainingNutritionBudget remaining = calculator.calculate(
                policy,
                List.of(NutritionEstimate.confirmed(new NutritionVector(2000, 100, 220, 70)))
        );

        assertThat(remaining.calories().maximum()).isZero();
        assertThat(remaining.feasible()).isFalse();
    }
}
