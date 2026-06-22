package com.healthcare.domain.diet.recommendation.candidate;

import java.util.List;

public record DietRecommendationCandidates(
        List<DietRecommendationCandidate> foods,
        CandidatePoolDiagnostics diagnostics
) {
    public DietRecommendationCandidates {
        foods = foods == null ? List.of() : List.copyOf(foods);
        diagnostics = diagnostics == null ? CandidatePoolDiagnostics.empty() : diagnostics;
    }

    /** 진단 없이 후보만 감싼다(테스트·하위호환용). */
    public DietRecommendationCandidates(List<DietRecommendationCandidate> foods) {
        this(foods, CandidatePoolDiagnostics.empty());
    }
}
