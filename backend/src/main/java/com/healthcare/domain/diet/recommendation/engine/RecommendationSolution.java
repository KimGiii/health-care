package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;

import java.util.List;

/**
 * 하루 추천 해 하나. 끼니 구성과 그 해에 대한 근거를 함께 묶는다.
 *
 * <p>대안을 상단 추천으로 교체하면 영양 합계와 근거도 함께 바뀌므로, 각 해가 자신의
 * {@link RecommendationRationale}을 보유한다(계획 리뷰 P1-5).
 */
public record RecommendationSolution(
        List<RecommendedMeal> meals,
        RecommendationRationale rationale
) {
    public RecommendationSolution {
        meals = meals == null ? List.of() : List.copyOf(meals);
    }
}
