package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodCatalogImportCheckpointRepository
        extends JpaRepository<FoodCatalogImportCheckpoint, FoodCatalogSource> {
}
