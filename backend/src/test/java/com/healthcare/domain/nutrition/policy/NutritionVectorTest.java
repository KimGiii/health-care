package com.healthcare.domain.nutrition.policy;

import com.healthcare.domain.nutrition.dto.NutritionTargets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NutritionVector")
class NutritionVectorTest {

    @Test
    @DisplayName("NutritionTargets(int)를 double 벡터로 변환해 int/double 이중 표현을 해소한다")
    void from_NutritionTargets_preservesAllFieldsAsDouble() {
        NutritionTargets targets = new NutritionTargets(2000, 150, 220, 60);

        NutritionVector vector = NutritionVector.from(targets);

        assertThat(vector.calories()).isEqualTo(2000.0);
        assertThat(vector.proteinG()).isEqualTo(150.0);
        assertThat(vector.carbsG()).isEqualTo(220.0);
        assertThat(vector.fatG()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("목표값이 0이면 벡터 필드도 0.0이다")
    void from_zeroTargets_producesZeroVector() {
        NutritionVector vector = NutritionVector.from(new NutritionTargets(0, 0, 0, 0));

        assertThat(vector.calories()).isZero();
        assertThat(vector.proteinG()).isZero();
        assertThat(vector.carbsG()).isZero();
        assertThat(vector.fatG()).isZero();
    }
}
