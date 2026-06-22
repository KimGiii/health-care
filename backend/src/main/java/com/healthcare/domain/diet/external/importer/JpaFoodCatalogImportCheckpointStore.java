package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JpaFoodCatalogImportCheckpointStore implements FoodCatalogImportCheckpointStore {

    private final FoodCatalogImportCheckpointRepository repository;

    @Override
    @Transactional(readOnly = true)
    public int nextPage(FoodCatalogSource source) {
        return repository.findById(source)
                .map(FoodCatalogImportCheckpoint::nextPage)
                .orElse(1);
    }

    @Override
    @Transactional
    public void markPageCompleted(FoodCatalogSource source, int pageNo) {
        FoodCatalogImportCheckpoint checkpoint = repository.findById(source)
                .orElseGet(() -> new FoodCatalogImportCheckpoint(source));
        checkpoint.markPageCompleted(pageNo);
        repository.save(checkpoint);
    }
}
