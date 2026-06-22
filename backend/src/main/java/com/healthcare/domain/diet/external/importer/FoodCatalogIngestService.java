package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
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
            created++;
        }

        return new FoodCatalogImportResult(created, updated, skipped, rejectedRows);
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
    }
}
