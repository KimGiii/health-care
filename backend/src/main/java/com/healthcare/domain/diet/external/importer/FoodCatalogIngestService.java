package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.external.dedup.CanonicalDedupResolver;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FoodCatalogIngestService {

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodServingOptionRepository servingOptionRepository;
    private final ServingOptionDeriver servingOptionDeriver;
    private final CanonicalDedupResolver dedupResolver;

    @Transactional
    public FoodCatalogImportResult ingest(
            List<FoodCatalogIngestCandidate> candidates,
            FoodCatalogIngestCurationMode curationMode) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<FoodCatalogImportRejectedRow> rejectedRows = new ArrayList<>();

        for (FoodCatalogIngestCandidate candidate : candidates) {
            if (!candidate.accepted()) {
                skipped++;
                if (candidate.rejection() != null) {
                    rejectedRows.add(candidate.rejection());
                }
                continue;
            }

            FoodCatalog imported = candidate.food();
            Optional<FoodCatalog> existing = foodCatalogRepository
                    .findBySourceAndFoodCode(imported.getSource(), imported.getFoodCode());

            if (existing.isPresent()) {
                updateExisting(existing.get(), imported, curationMode);
                updated++;
                continue;
            }

            foodCatalogRepository.save(imported);
            // dedup 정규화 후 대표 여부가 확정되므로 제공량 옵션 동기화보다 먼저 수행한다.
            dedupResolver.resolve(imported);
            syncServingOptions(imported, true);
            created++;
        }

        return new FoodCatalogImportResult(created, updated, skipped, rejectedRows);
    }

    /**
     * 식품의 1회 제공량 옵션을 보장한다. 옵션이 없는 식품은 추천에서 제외되므로(§7.1),
     * 적재·재적재 시 현실적 제공량 옵션을 두되, 강제 갱신이 아니면 기존 옵션은 유지한다.
     *
     * <p>dedup 패자(superseded)는 추천 대상이 아니므로 옵션을 생성하지 않고, 남은 옵션이 있으면
     * 정리해 옵션 행 폭증을 막는다(설계 §7 — 대표 행에만 옵션 보유). 단, 다른 행 적재로 강등된
     * 구 대표의 옵션 정리는 그 행이 재적재될 때 수렴한다(후속 백필 보완 대상).
     */
    private void syncServingOptions(FoodCatalog food, boolean forceRefresh) {
        if (food.getId() == null) {
            return;
        }
        if (!food.isCanonicalRow()) {
            servingOptionRepository.deleteByFoodCatalogId(food.getId());
            return;
        }
        if (!forceRefresh && servingOptionRepository.existsByFoodCatalogId(food.getId())) {
            return;
        }
        servingOptionRepository.deleteByFoodCatalogId(food.getId());
        // 낱개 식품(계란 등) 판별을 위해 음식 이름을 전달한다. nameKo 우선, 없으면 name fallback.
        String foodName = food.getNameKo() != null ? food.getNameKo() : food.getName();
        servingOptionRepository.saveAll(
                servingOptionDeriver.derive(
                        food.getId(), food.getCategory(), food.getServingReference(), foodName));
    }

    Optional<FoodCatalog> findBySourceAndFoodCode(FoodCatalogSource source, String foodCode) {
        return foodCatalogRepository.findBySourceAndFoodCode(source, foodCode);
    }

    private void updateExisting(
            FoodCatalog existing,
            FoodCatalog imported,
            FoodCatalogIngestCurationMode curationMode) {
        boolean factsChanged = existing.recommendationFactsDifferFrom(imported);
        existing.updateSourceFactsFromImportedCatalog(imported);
        if (curationMode == FoodCatalogIngestCurationMode.REPLACE_FROM_IMPORT) {
            existing.updateCuration(imported.curation());
        } else if (factsChanged) {
            // PRESERVE 모드(공공데이터 재적재)에서 추천 후보의 핵심 사실이 바뀌면
            // 기존 검수 근거가 무효화되므로 자격을 회수하고 재검증 대상으로 강등한다. (계획 §5.1)
            existing.revokeRecommendationForStaleFacts();
        }
        foodCatalogRepository.save(existing);
        dedupResolver.resolve(existing);
        // 사실 변경·REPLACE면 옵션 재도출, 아니면 옵션이 없을 때만 backfill(기존 적재분).
        boolean forceRefresh = factsChanged
                || curationMode == FoodCatalogIngestCurationMode.REPLACE_FROM_IMPORT;
        syncServingOptions(existing, forceRefresh);
    }
}
