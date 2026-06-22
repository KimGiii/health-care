package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
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
            syncServingOptions(imported, true);
            created++;
        }

        return new FoodCatalogImportResult(created, updated, skipped, rejectedRows);
    }

    /**
     * 식품의 1회 제공량 옵션을 보장한다. 옵션이 없는 식품은 추천에서 제외되므로(§7.1),
     * 적재·재적재 시 항상 현실적 제공량 옵션을 두되, 강제 갱신이 아니면 기존 옵션은 유지한다.
     */
    private void syncServingOptions(FoodCatalog food, boolean forceRefresh) {
        if (food.getId() == null) {
            return;
        }
        if (!forceRefresh && servingOptionRepository.existsByFoodCatalogId(food.getId())) {
            return;
        }
        servingOptionRepository.deleteByFoodCatalogId(food.getId());
        servingOptionRepository.saveAll(
                servingOptionDeriver.derive(food.getId(), food.getCategory(), food.getServingReference()));
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
        // 사실 변경·REPLACE면 옵션 재도출, 아니면 옵션이 없을 때만 backfill(기존 적재분).
        boolean forceRefresh = factsChanged
                || curationMode == FoodCatalogIngestCurationMode.REPLACE_FROM_IMPORT;
        syncServingOptions(existing, forceRefresh);
    }
}
