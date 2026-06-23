package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.DedupState;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기존 DB의 dedup 미정규화 행을 출처 우선순위 canonical로 1회 수렴시키는 백필 패스(설계 §7 옵션 B).
 *
 * <p>적재 경로({@link com.healthcare.domain.diet.external.importer.FoodCatalogIngestService})는 행별로
 * {@link CanonicalDedupResolver}를 호출하지만, V39 이전에 각 출처가 독립적으로 적재돼 모두 대표로 남은
 * 행들은 별도 백필이 필요하다. resolver는 출처 적재 순서와 무관하게 멱등 수렴하므로 그대로 재사용한다.
 *
 * <p>옵션 정리는 resolve 전량 완료 <b>후</b> 일괄 수행한다. 단일 패스 인라인 정리는 커서가 이미 지나간
 * 구 대표(나중에 강등됨)의 옵션을 놓치기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class FoodCatalogCanonicalBackfillService {

    static final int DEFAULT_BATCH_SIZE = 200;

    private final FoodCatalogRepository catalogRepository;
    private final FoodServingOptionRepository servingOptionRepository;
    private final CanonicalDedupResolver dedupResolver;
    private final EntityManager entityManager;

    @Transactional
    public FoodCatalogCanonicalBackfillSummary backfill() {
        return backfill(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public FoodCatalogCanonicalBackfillSummary backfill(int batchSize) {
        long afterId = 0L;
        int processed = 0;
        int batches = 0;

        while (true) {
            List<FoodCatalog> rows = catalogRepository.findDedupEligibleAfterId(afterId, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            for (FoodCatalog row : rows) {
                dedupResolver.resolve(row);
                processed++;
                if (row.getId() > afterId) {
                    afterId = row.getId();
                }
            }
            // 배치 경계마다 1차 캐시를 비워 flush 비용이 누적 엔티티 수에 비례(O(n²))하지 않게 한다.
            // resolver가 이미 행별로 saveAndFlush 했으므로 미반영 변경 유실 없이 안전하다.
            entityManager.flush();
            entityManager.clear();
            batches++;
        }

        int clearedOptions = servingOptionRepository.deleteOptionsForSupersededRows();

        return new FoodCatalogCanonicalBackfillSummary(
                processed,
                batches,
                catalogRepository.countByIsCustomFalseAndDedupState(DedupState.CANONICAL),
                catalogRepository.countByIsCustomFalseAndDedupState(DedupState.SUPERSEDED),
                catalogRepository.countByIsCustomFalseAndDedupState(DedupState.COLLISION),
                clearedOptions
        );
    }
}
