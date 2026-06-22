package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalogSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "food_catalog_import_checkpoints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodCatalogImportCheckpoint {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodCatalogSource source;

    @Column(name = "last_completed_page", nullable = false)
    private int lastCompletedPage;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public FoodCatalogImportCheckpoint(FoodCatalogSource source) {
        this.source = source;
        this.lastCompletedPage = 0;
        this.updatedAt = OffsetDateTime.now();
    }

    public int nextPage() {
        return lastCompletedPage + 1;
    }

    public void markPageCompleted(int pageNo) {
        this.lastCompletedPage = pageNo;
        this.updatedAt = OffsetDateTime.now();
    }
}
