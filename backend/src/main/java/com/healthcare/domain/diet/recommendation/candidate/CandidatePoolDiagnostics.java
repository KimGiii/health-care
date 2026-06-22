package com.healthcare.domain.diet.recommendation.candidate;

/**
 * 후보 풀 단계별 탈락 집계 (계획 리뷰 P0-3).
 *
 * <p>풀은 통과 후보만 반환하므로, 추천 실패 사유를 분류하려면 어떤 단계에서 후보가 사라졌는지
 * 알아야 한다. use case가 이 진단과 엔진 결과를 조합해 구조화된 실패 사유를 판정한다.
 *
 * @param passedCount         최종 통과 후보 수
 * @param restrictionFiltered 사용자 제한 조건(식품·카테고리·키워드)으로 탈락한 수
 * @param freshnessExpired    데이터 신선도 정책으로 탈락한 수
 * @param incompleteMacro     핵심 영양 데이터 결측으로 추천에 부적합한 수
 * @param allergenRejected    알러젠 안전 게이트에서 탈락한 수
 */
public record CandidatePoolDiagnostics(
        int passedCount,
        int restrictionFiltered,
        int freshnessExpired,
        int incompleteMacro,
        int allergenRejected
) {
    public static CandidatePoolDiagnostics empty() {
        return new CandidatePoolDiagnostics(0, 0, 0, 0, 0);
    }

    /** 통과 후보가 하나도 없는가. */
    public boolean isEmptyPool() {
        return passedCount == 0;
    }

    /**
     * 통과 0일 때 지배적 탈락 원인을 판정한다.
     * 알러젠 → 결측 → 제한조건 → 신선도 순으로 우선순위를 둔다(안전·데이터 신뢰 우선).
     * 통과 후보가 있으면 null.
     */
    public DominantDropCause dominantDropCause() {
        if (!isEmptyPool()) {
            return null;
        }
        if (allergenRejected > 0
                && allergenRejected >= incompleteMacro
                && allergenRejected >= restrictionFiltered) {
            return DominantDropCause.ALLERGEN;
        }
        if (incompleteMacro > 0 && incompleteMacro >= restrictionFiltered) {
            return DominantDropCause.INCOMPLETE_MACRO;
        }
        if (restrictionFiltered > 0) {
            return DominantDropCause.RESTRICTION;
        }
        if (freshnessExpired > 0) {
            return DominantDropCause.FRESHNESS;
        }
        return DominantDropCause.NONE;
    }

    public enum DominantDropCause {
        ALLERGEN, INCOMPLETE_MACRO, RESTRICTION, FRESHNESS, NONE
    }
}
