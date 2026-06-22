package com.healthcare.domain.diet.recommendation.engine;

/**
 * 식품별 온라인 참여 집계 결과. 추천 이벤트에서 산출하는 순위 신호의 입력 단위다.
 *
 * <ul>
 *   <li>{@code recordedCount} — 추천이 실제 식단 기록으로 전환된 횟수 (긍정 신호)</li>
 *   <li>{@code refreshedCount} — 사용자가 다시 추천을 요청해 회피한 횟수 (부정 신호)</li>
 * </ul>
 *
 * <p>현재 추천 이벤트({@code recommendation_events})는 snapshot 단위라 식품별 매핑이 없다.
 * 식품별 집계는 스냅샷 항목-식품 연결을 보강한 뒤 채울 수 있으며, 이 record는 그 집계 결과를
 * 받는 계약이다.
 */
public record FoodEngagementStat(long recordedCount, long refreshedCount) {

    public FoodEngagementStat {
        if (recordedCount < 0 || refreshedCount < 0) {
            throw new IllegalArgumentException("engagement counts must be non-negative");
        }
    }
}
