package com.healthcare.domain.diet.recommendation.candidate;

import java.util.List;

public record DietRecommendationCandidates(
        List<DietRecommendationCandidate> foods
) {
    public DietRecommendationCandidates {
        foods = foods == null ? List.of() : List.copyOf(foods);
    }
}
