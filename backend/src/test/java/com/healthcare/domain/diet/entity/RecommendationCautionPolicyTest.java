package com.healthcare.domain.diet.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendationCautionPolicy")
class RecommendationCautionPolicyTest {

    @Test
    @DisplayName("1회 제공량 기준 나트륨 600mg 이상이면 주의 추천 사유가 필요하다")
    void assess_sodiumAtThreshold_requiresCaution() {
        RecommendationCautionPolicy.Assessment assessment =
                RecommendationCautionPolicy.assess(600.0, 0.0, 0.0);

        assertThat(assessment.requiresCaution()).isTrue();
        assertThat(assessment.defaultReason()).isEqualTo("나트륨 주의");
    }

    @Test
    @DisplayName("1회 제공량 기준 당류 15g 이상이면 주의 추천 사유가 필요하다")
    void assess_sugarsAtThreshold_requiresCaution() {
        RecommendationCautionPolicy.Assessment assessment =
                RecommendationCautionPolicy.assess(0.0, 15.0, 0.0);

        assertThat(assessment.requiresCaution()).isTrue();
        assertThat(assessment.defaultReason()).isEqualTo("당류 주의");
    }

    @Test
    @DisplayName("1회 제공량 기준 포화지방 4.5g 이상이면 주의 추천 사유가 필요하다")
    void assess_saturatedFatAtThreshold_requiresCaution() {
        RecommendationCautionPolicy.Assessment assessment =
                RecommendationCautionPolicy.assess(0.0, 0.0, 4.5);

        assertThat(assessment.requiresCaution()).isTrue();
        assertThat(assessment.defaultReason()).isEqualTo("포화지방 주의");
    }

    @Test
    @DisplayName("여러 기준을 넘으면 나트륨, 당류, 포화지방 순서로 주의 사유를 만든다")
    void assess_multipleThresholdsExceeded_combinesReasonsInPolicyOrder() {
        RecommendationCautionPolicy.Assessment assessment =
                RecommendationCautionPolicy.assess(600.0, 15.0, 4.5);

        assertThat(assessment.requiresCaution()).isTrue();
        assertThat(assessment.defaultReason()).isEqualTo("나트륨/당류/포화지방 주의");
    }

    @Test
    @DisplayName("세 기준이 모두 미만이면 주의 추천 사유가 필요하지 않다")
    void assess_allBelowThreshold_doesNotRequireCaution() {
        RecommendationCautionPolicy.Assessment assessment =
                RecommendationCautionPolicy.assess(599.9, 14.9, 4.4);

        assertThat(assessment.requiresCaution()).isFalse();
        assertThat(assessment.defaultReason()).isNull();
    }
}
