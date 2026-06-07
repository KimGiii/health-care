package com.healthcare.domain.nutrition;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.nutrition.service.NutritionCalculator;
import com.healthcare.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class NutritionCalculatorTest {

    @Nested
    @DisplayName("BMR (Mifflin-St Jeor)")
    class Bmr {

        @Test
        @DisplayName("남성 30세 80kg 175cm → 10*80 + 6.25*175 − 5*30 + 5 = 1748.75")
        void male30() {
            double bmr = NutritionCalculator.calculateBmr(User.Sex.MALE, 80.0, 175.0, 30);
            assertThat(bmr).isCloseTo(1748.75, offset(0.01));
        }

        @Test
        @DisplayName("여성 25세 60kg 165cm → 10*60 + 6.25*165 − 5*25 − 161 = 1345.25")
        void female25() {
            double bmr = NutritionCalculator.calculateBmr(User.Sex.FEMALE, 60.0, 165.0, 25);
            assertThat(bmr).isCloseTo(1345.25, offset(0.01));
        }

        @Test
        @DisplayName("OTHER는 남녀 평균 (offset -78)")
        void other() {
            double bmr = NutritionCalculator.calculateBmr(User.Sex.OTHER, 70.0, 170.0, 30);
            // 10*70 + 6.25*170 − 5*30 + (-78) = 1534.5
            assertThat(bmr).isCloseTo(1534.5, offset(0.01));
        }
    }

    @Nested
    @DisplayName("TDEE — 활동계수")
    class Tdee {

        @ParameterizedTest(name = "{0} → BMR × {1}")
        @CsvSource({
            "SEDENTARY, 1.2",
            "LIGHTLY_ACTIVE, 1.375",
            "MODERATELY_ACTIVE, 1.55",
            "VERY_ACTIVE, 1.725",
            "EXTRA_ACTIVE, 1.9"
        })
        void activityFactor(User.ActivityLevel level, double expectedFactor) {
            double bmr = 1500.0;
            double tdee = NutritionCalculator.calculateTdee(bmr, level);
            assertThat(tdee).isCloseTo(1500.0 * expectedFactor, offset(0.01));
        }
    }

    @Nested
    @DisplayName("목표별 칼로리 조정")
    class GoalAdjustment {

        private static final double TDEE = 2500.0;

        @Test
        void weightLoss_minus500() {
            assertThat(NutritionCalculator.adjustKcalForGoal(TDEE, Goal.GoalType.WEIGHT_LOSS))
                .isEqualTo(2000.0);
        }

        @Test
        void muscleGain_plus300() {
            assertThat(NutritionCalculator.adjustKcalForGoal(TDEE, Goal.GoalType.MUSCLE_GAIN))
                .isEqualTo(2800.0);
        }

        @Test
        void bodyRecomposition_minus200() {
            assertThat(NutritionCalculator.adjustKcalForGoal(TDEE, Goal.GoalType.BODY_RECOMPOSITION))
                .isEqualTo(2300.0);
        }

        @Test
        void endurance_plus200() {
            assertThat(NutritionCalculator.adjustKcalForGoal(TDEE, Goal.GoalType.ENDURANCE))
                .isEqualTo(2700.0);
        }

        @Test
        void generalHealth_maintain() {
            assertThat(NutritionCalculator.adjustKcalForGoal(TDEE, Goal.GoalType.GENERAL_HEALTH))
                .isEqualTo(2500.0);
        }

        @Test
        @DisplayName("null이면 TDEE 그대로 (목표 없음)")
        void nullGoal_keepsTdee() {
            assertThat(NutritionCalculator.adjustKcalForGoal(TDEE, null)).isEqualTo(2500.0);
        }
    }

    @Nested
    @DisplayName("안전 하한")
    class SafetyFloor {

        @Test
        @DisplayName("여성 1200 미만 → 1200")
        void femaleFloor() {
            assertThat(NutritionCalculator.applySafetyFloor(900.0, User.Sex.FEMALE)).isEqualTo(1200);
        }

        @Test
        @DisplayName("남성 1500 미만 → 1500")
        void maleFloor() {
            assertThat(NutritionCalculator.applySafetyFloor(1200.0, User.Sex.MALE)).isEqualTo(1500);
        }

        @Test
        @DisplayName("OTHER도 남성과 동일 하한 (1500)")
        void otherSameAsMale() {
            assertThat(NutritionCalculator.applySafetyFloor(1300.0, User.Sex.OTHER)).isEqualTo(1500);
        }

        @Test
        @DisplayName("하한 위 값은 그대로 반올림 통과")
        void aboveFloorPassesThrough() {
            assertThat(NutritionCalculator.applySafetyFloor(2347.6, User.Sex.MALE)).isEqualTo(2348);
        }
    }

    @Nested
    @DisplayName("매크로 분배")
    class Macros {

        @Test
        @DisplayName("WEIGHT_LOSS — 단백질 2.0g/kg, 지방 25%")
        void weightLoss() {
            // 75kg, 2000 kcal
            NutritionTargets t = NutritionCalculator.distributeMacros(2000, 75.0, Goal.GoalType.WEIGHT_LOSS);
            assertThat(t.proteinTargetG()).isEqualTo(150);             // 75 * 2.0
            assertThat(t.fatTargetG()).isEqualTo(56);                  // 2000 * 0.25 / 9 = 55.55 ≈ 56
            // 단백질 600 + 지방 500 = 1100, 남은 900 / 4 = 225g
            assertThat(t.carbTargetG()).isEqualTo(225);
            assertThat(t.calorieTarget()).isEqualTo(2000);
        }

        @Test
        @DisplayName("MUSCLE_GAIN — 단백질 1.8g/kg, 지방 25%")
        void muscleGain() {
            NutritionTargets t = NutritionCalculator.distributeMacros(2800, 75.0, Goal.GoalType.MUSCLE_GAIN);
            assertThat(t.proteinTargetG()).isEqualTo(135);             // 75 * 1.8
            assertThat(t.fatTargetG()).isEqualTo(78);                  // 2800 * 0.25 / 9 = 77.77 ≈ 78
            assertThat(t.carbTargetG()).isEqualTo(390);                // 단백질 540 + 지방 700 = 1240, 남은 1560/4=390
            assertThat(t.calorieTarget()).isEqualTo(2800);
        }

        @Test
        @DisplayName("GENERAL_HEALTH — 단백질 1.2g/kg, 지방 30%")
        void generalHealth() {
            NutritionTargets t = NutritionCalculator.distributeMacros(2200, 70.0, Goal.GoalType.GENERAL_HEALTH);
            assertThat(t.proteinTargetG()).isEqualTo(84);              // 70 * 1.2
            assertThat(t.fatTargetG()).isEqualTo(73);                  // 2200 * 0.30 / 9 = 73.33 ≈ 73
            // 단백질 336 + 지방 660 = 996, 남은 1204/4 = 301
            assertThat(t.carbTargetG()).isEqualTo(301);
        }

        @Test
        @DisplayName("goalType이 null이면 GENERAL_HEALTH로 처리")
        void nullGoalType_usesGeneralHealth() {
            NutritionTargets t = NutritionCalculator.distributeMacros(2200, 70.0, null);
            assertThat(t.proteinTargetG()).isEqualTo(84);
            assertThat(t.fatTargetG()).isEqualTo(73);
        }
    }

    @Nested
    @DisplayName("통합 — computeFor")
    class ComputeFor {

        private User user(User.Sex sex, int yearsAgo, double height, double weight, User.ActivityLevel level) {
            return User.builder()
                .id(1L)
                .email("t@t.com")
                .passwordHash("x")
                .displayName("t")
                .sex(sex)
                .dateOfBirth(LocalDate.now().minusYears(yearsAgo))
                .heightCm(height)
                .weightKg(weight)
                .activityLevel(level)
                .build();
        }

        @Test
        @DisplayName("남성 30세 80kg 175cm MODERATELY_ACTIVE + MUSCLE_GAIN")
        void typicalMaleMuscleGain() {
            User u = user(User.Sex.MALE, 30, 175.0, 80.0, User.ActivityLevel.MODERATELY_ACTIVE);
            NutritionTargets t = NutritionCalculator.computeFor(u, Goal.GoalType.MUSCLE_GAIN);

            // BMR = 1748.75, TDEE = 1748.75 * 1.55 = 2710.5625
            // +300 = 3010.5625 → 안전 하한 통과 → 3011 (반올림)
            assertThat(t.calorieTarget()).isEqualTo(3011);
            // 단백질 = 80 * 1.8 = 144g
            assertThat(t.proteinTargetG()).isEqualTo(144);
        }

        @Test
        @DisplayName("프로필 누락 시 IllegalStateException")
        void missingProfile_throws() {
            User u = User.builder()
                .id(1L).email("t@t.com").passwordHash("x").displayName("t")
                .sex(null)
                .build();
            assertThatThrownBy(() -> NutritionCalculator.computeFor(u, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("프로필 정보가 부족");
        }

        @Test
        @DisplayName("canCalculate — 모든 필수 필드 있으면 true")
        void canCalculate_allPresent() {
            User u = user(User.Sex.FEMALE, 25, 160.0, 55.0, User.ActivityLevel.LIGHTLY_ACTIVE);
            assertThat(NutritionCalculator.canCalculate(u)).isTrue();
        }

        @Test
        @DisplayName("canCalculate — 생년월일 누락 → false")
        void canCalculate_missingDob() {
            User u = User.builder()
                .id(1L).email("t@t.com").passwordHash("x").displayName("t")
                .sex(User.Sex.MALE)
                .heightCm(175.0).weightKg(80.0)
                .activityLevel(User.ActivityLevel.SEDENTARY)
                .build();
            assertThat(NutritionCalculator.canCalculate(u)).isFalse();
        }
    }
}
