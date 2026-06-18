package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.external.importer.FoodDisplayNameNormalizer;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 이미 적재된 행의 표시명을 정규화 휴리스틱으로 백필한다.
 *
 * <p>MFDS 원본 어순({@code 경단_깨})을 자연어 표시명({@code 깨경단})으로 바꾸고 검색 별칭을 채운다.
 * importer가 앞으로의 적재/새 환경을 처리하므로, 이 작업은 기존 DB 1회 보정용이다.
 * id 커서로 배치를 전진시켜 재실행해도 안전하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodCatalogNameRenormalizationService {

    /** name(150) / search_alias(400) 컬럼 길이에 맞춘 상한. */
    private static final int NAME_MAX_LENGTH = 150;
    private static final int SEARCH_ALIAS_MAX_LENGTH = 400;
    private static final int BATCH_SIZE = 500;

    private final FoodCatalogRepository repository;

    /**
     * 원본 어순이 남은 모든 행을 표시명으로 정규화한다.
     *
     * @return 처리한 행 수와 배치 수
     */
    public RenormalizationResult renormalizeAll() {
        long cursor = 0;
        int total = 0;
        int batches = 0;
        while (true) {
            BatchResult result = renormalizeBatch(cursor);
            if (result.processed() == 0) {
                break;
            }
            cursor = result.lastId();
            total += result.processed();
            batches++;
        }
        log.info("표시명 재정규화 완료: processed={}, batches={}", total, batches);
        return new RenormalizationResult(total, batches);
    }

    private BatchResult renormalizeBatch(long afterId) {
        List<FoodCatalog> rows = repository.findUnderscoreNamesAfterId(afterId, BATCH_SIZE);
        if (rows.isEmpty()) {
            return new BatchResult(0, afterId);
        }
        for (FoodCatalog row : rows) {
            FoodDisplayNameNormalizer.NormalizedName normalized =
                    FoodDisplayNameNormalizer.normalize(row.getName());
            row.applyDisplayNormalization(
                    truncate(normalized.displayName(), NAME_MAX_LENGTH),
                    truncate(normalized.searchAlias(), SEARCH_ALIAS_MAX_LENGTH));
        }
        // detached 엔티티를 merge로 영속화 (saveAll은 자체 트랜잭션에서 실행)
        repository.saveAll(rows);
        long lastId = rows.get(rows.size() - 1).getId();
        return new BatchResult(rows.size(), lastId);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record BatchResult(int processed, long lastId) {
    }

    public record RenormalizationResult(int processedCount, int batchCount) {
    }
}
