package com.healthcare.domain.diet.external.dedup;

/**
 * canonical 백필 패스 실행 결과 요약.
 *
 * @param processedRows           resolve를 적용한 dedup 대상 행 수
 * @param batchCount              id 커서로 순회한 배치 수
 * @param canonicalRows           백필 후 대표(CANONICAL) 비커스텀 행 수
 * @param supersededRows          백필 후 패자(SUPERSEDED) 비커스텀 행 수
 * @param collisionRows           백필 후 충돌(COLLISION) 비커스텀 행 수(검토 큐)
 * @param servingOptionsCleared   패자 행에서 정리된 제공량 옵션 행 수
 */
public record FoodCatalogCanonicalBackfillSummary(
        int processedRows,
        int batchCount,
        long canonicalRows,
        long supersededRows,
        long collisionRows,
        int servingOptionsCleared
) {
}
