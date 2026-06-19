package com.healthcare.domain.diet.allergen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AllergenConfidenceLevel — 검증 프로필 근거 등급")
class AllergenConfidenceLevelTest {

    @Test
    @DisplayName("DIRECT_VERIFIED·LABEL_DERIVED는 검증 프로필 근거가 될 수 있다")
    void isProfileGrade_highConfidence_true() {
        assertThat(AllergenConfidenceLevel.DIRECT_VERIFIED.isProfileGrade()).isTrue();
        assertThat(AllergenConfidenceLevel.LABEL_DERIVED.isProfileGrade()).isTrue();
    }

    @Test
    @DisplayName("RECIPE_DERIVED·UNKNOWN은 검증 프로필 근거가 될 수 없다")
    void isProfileGrade_lowConfidence_false() {
        assertThat(AllergenConfidenceLevel.RECIPE_DERIVED.isProfileGrade()).isFalse();
        assertThat(AllergenConfidenceLevel.UNKNOWN.isProfileGrade()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AllergenConfidenceLevel.class)
    @DisplayName("모든 신뢰도 값은 검증 등급 여부를 명시적으로 답한다")
    void isProfileGrade_definedForAllValues(AllergenConfidenceLevel level) {
        boolean expected = level == AllergenConfidenceLevel.DIRECT_VERIFIED
                || level == AllergenConfidenceLevel.LABEL_DERIVED;
        assertThat(level.isProfileGrade()).isEqualTo(expected);
    }
}
