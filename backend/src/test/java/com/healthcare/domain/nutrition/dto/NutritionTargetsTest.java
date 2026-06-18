package com.healthcare.domain.nutrition.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NutritionTargets 단위 테스트")
class NutritionTargetsTest {

    @Nested
    @DisplayName("minus(consumed) — 남은 영양 목표 계산")
    class Minus {

        @Test
        @DisplayName("섭취량이 없으면 원래 목표를 그대로 반환한다")
        void minus_noConsumed_returnsOriginal() {
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            ConsumedNutrients none = new ConsumedNutrients(0, 0, 0, 0);

            NutritionTargets remaining = targets.minus(none);

            assertThat(remaining.calorieTarget()).isEqualTo(2000);
            assertThat(remaining.proteinTargetG()).isEqualTo(150);
            assertThat(remaining.carbTargetG()).isEqualTo(230);
            assertThat(remaining.fatTargetG()).isEqualTo(67);
        }

        @Test
        @DisplayName("섭취량을 차감한 남은 목표를 반환한다")
        void minus_partialConsumed_returnsRemainder() {
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            ConsumedNutrients consumed = new ConsumedNutrients(500, 40, 60, 15);

            NutritionTargets remaining = targets.minus(consumed);

            assertThat(remaining.calorieTarget()).isEqualTo(1500);
            assertThat(remaining.proteinTargetG()).isEqualTo(110);
            assertThat(remaining.carbTargetG()).isEqualTo(170);
            assertThat(remaining.fatTargetG()).isEqualTo(52);
        }

        @Test
        @DisplayName("목표를 초과 섭취하면 해당 영양소 목표는 0으로 클램핑된다")
        void minus_overConsumed_clampedToZero() {
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            ConsumedNutrients consumed = new ConsumedNutrients(2500, 200, 300, 80);

            NutritionTargets remaining = targets.minus(consumed);

            assertThat(remaining.calorieTarget()).isZero();
            assertThat(remaining.proteinTargetG()).isZero();
            assertThat(remaining.carbTargetG()).isZero();
            assertThat(remaining.fatTargetG()).isZero();
        }

        @Test
        @DisplayName("일부 영양소만 초과하면 해당 영양소만 0이 된다")
        void minus_partialOverConsumed_onlyExceededIsZero() {
            NutritionTargets targets = new NutritionTargets(2000, 150, 230, 67);
            ConsumedNutrients consumed = new ConsumedNutrients(500, 200, 60, 15);

            NutritionTargets remaining = targets.minus(consumed);

            assertThat(remaining.calorieTarget()).isEqualTo(1500);
            assertThat(remaining.proteinTargetG()).isZero();
            assertThat(remaining.carbTargetG()).isEqualTo(170);
            assertThat(remaining.fatTargetG()).isEqualTo(52);
        }
    }
}
