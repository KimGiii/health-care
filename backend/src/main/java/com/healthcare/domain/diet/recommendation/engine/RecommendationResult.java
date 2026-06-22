package com.healthcare.domain.diet.recommendation.engine;

import java.util.List;

/**
 * 제약 최적화 엔진의 결과. feasible 해 묶음(Success) 또는 구조화된 실패(Failure)다.
 *
 * <p>근접 식단을 성공처럼 노출하지 않는다(계획 §8.3, §13 비범위). 실패는 항상 명시적 사유를 갖는다.
 */
public sealed interface RecommendationResult
        permits RecommendationResult.Success, RecommendationResult.Failure {

    boolean succeeded();

    /**
     * feasible 해 묶음. {@code solutions.get(0)}이 primary, 나머지가 다양성 정렬된 대안이다.
     */
    record Success(List<RecommendationSolution> solutions) implements RecommendationResult {
        public Success {
            if (solutions == null || solutions.isEmpty()) {
                throw new IllegalArgumentException("Success requires at least one solution");
            }
            solutions = List.copyOf(solutions);
        }

        @Override
        public boolean succeeded() {
            return true;
        }

        public RecommendationSolution primary() {
            return solutions.get(0);
        }

        public List<RecommendationSolution> alternatives() {
            return solutions.subList(1, solutions.size());
        }
    }

    /** 엔진이 판정한 구조화된 실패. */
    record Failure(RecommendationFailureReason reason) implements RecommendationResult {
        public Failure {
            if (reason == null) {
                throw new IllegalArgumentException("Failure requires a reason");
            }
        }

        @Override
        public boolean succeeded() {
            return false;
        }
    }

    static Success success(List<RecommendationSolution> solutions) {
        return new Success(solutions);
    }

    static Failure failure(RecommendationFailureReason reason) {
        return new Failure(reason);
    }
}
