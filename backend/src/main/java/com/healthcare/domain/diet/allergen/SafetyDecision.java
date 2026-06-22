package com.healthcare.domain.diet.allergen;

/**
 * AllergenSafetyGate의 판단 결과.
 * passes=true면 추천 후보로 진행하고, confidence는 DietRecommendationCandidate에 기록한다.
 */
public record SafetyDecision(boolean passes, AllergenConfidenceLevel confidence) {

    static SafetyDecision block() {
        return new SafetyDecision(false, AllergenConfidenceLevel.UNKNOWN);
    }

    static SafetyDecision allow(AllergenConfidenceLevel confidence) {
        return new SafetyDecision(true, confidence);
    }
}
