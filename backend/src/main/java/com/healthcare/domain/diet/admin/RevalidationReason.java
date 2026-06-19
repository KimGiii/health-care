package com.healthcare.domain.diet.admin;

/**
 * 추천 후보가 재검증 큐에 오른 사유. enum 선언 순서가 큐 정렬 우선순위가 된다(앞일수록 먼저).
 */
public enum RevalidationReason {
    /** source 사실(영양·제공량 등) 변경으로 추천 자격이 자동 회수된 항목. (Phase 5 Unit 1) */
    REVOKED_STALE_FACTS,
    /** source별 신선도(최대 허용 연령) 정책상 데이터가 만료된 추천 후보. */
    DATA_EXPIRED
}
