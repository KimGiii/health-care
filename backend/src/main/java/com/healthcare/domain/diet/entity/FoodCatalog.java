package com.healthcare.domain.diet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;

@Entity
@Table(name = "food_catalog")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class FoodCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "name_ko", length = 150)
    private String nameKo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategory category;

    @Column(name = "calories_per_100g", nullable = false)
    private Double caloriesPer100g;

    @Column(name = "protein_per_100g")
    private Double proteinPer100g;

    @Column(name = "carbs_per_100g")
    private Double carbsPer100g;

    @Column(name = "fat_per_100g")
    private Double fatPer100g;

    @Column(name = "sugars_per_100g")
    private Double sugarsPer100g;

    @Column(name = "dietary_fiber_per_100g")
    private Double dietaryFiberPer100g;

    @Column(name = "saturated_fat_per_100g")
    private Double saturatedFatPer100g;

    @Column(name = "trans_fat_per_100g")
    private Double transFatPer100g;

    @Column(name = "cholesterol_per_100g_mg")
    private Double cholesterolPer100gMg;

    @Column(name = "sodium_per_100g_mg")
    private Double sodiumPer100gMg;

    @Column(name = "food_code", length = 60)
    private String foodCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private FoodCatalogSource source = FoodCatalogSource.SEED;

    @Column(name = "source_detail", length = 120)
    private String sourceDetail;

    @Column(name = "brand_name", length = 150)
    private String brandName;

    @Column(length = 150)
    private String maker;

    @Column(name = "serving_size_g")
    private Double servingSizeG;

    @Column(name = "serving_reference", length = 80)
    private String servingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_status", nullable = false, length = 40)
    @Builder.Default
    private RecommendationStatus recommendationStatus = RecommendationStatus.RECOMMENDABLE;

    @Column(name = "recommendation_reason", length = 255)
    private String recommendationReason;

    @Column(name = "data_version", length = 80)
    private String dataVersion;

    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Long usageCount = 0L;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public void incrementUsage() {
        this.usageCount = (this.usageCount == null ? 0L : this.usageCount) + 1;
    }

    public void decrementUsage() {
        this.usageCount = Math.max((this.usageCount == null ? 0L : this.usageCount) - 1, 0L);
    }

    public void updateSourceFactsFromImportedCatalog(FoodCatalog imported) {
        this.name = imported.name;
        this.nameKo = imported.nameKo;
        this.category = imported.category;
        this.caloriesPer100g = imported.caloriesPer100g;
        this.proteinPer100g = imported.proteinPer100g;
        this.carbsPer100g = imported.carbsPer100g;
        this.fatPer100g = imported.fatPer100g;
        this.sugarsPer100g = imported.sugarsPer100g;
        this.dietaryFiberPer100g = imported.dietaryFiberPer100g;
        this.saturatedFatPer100g = imported.saturatedFatPer100g;
        this.transFatPer100g = imported.transFatPer100g;
        this.cholesterolPer100gMg = imported.cholesterolPer100gMg;
        this.sodiumPer100gMg = imported.sodiumPer100gMg;
        this.sourceDetail = imported.sourceDetail;
        this.brandName = imported.brandName;
        this.maker = imported.maker;
        this.servingSizeG = imported.servingSizeG;
        this.servingReference = imported.servingReference;
        this.dataVersion = imported.dataVersion;
        this.lastVerifiedAt = imported.lastVerifiedAt;
        this.isCustom = imported.isCustom;
    }

    public RecommendationCuration curation() {
        return RecommendationCuration.of(recommendationStatus, recommendationReason);
    }

    public void updateCuration(RecommendationCuration curation) {
        this.recommendationStatus = curation.status();
        this.recommendationReason = curation instanceof RecommendationCuration.WithCaution c
                ? c.reason() : null;
    }


    public enum FoodCategory {
        GRAIN, PROTEIN_SOURCE, VEGETABLE, FRUIT, DAIRY, FAT, BEVERAGE, PROCESSED, OTHER
    }
}
