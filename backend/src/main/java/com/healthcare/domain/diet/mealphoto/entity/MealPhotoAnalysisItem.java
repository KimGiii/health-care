package com.healthcare.domain.diet.mealphoto.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "meal_photo_analysis_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class MealPhotoAnalysisItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(name = "item_order", nullable = false)
    private Integer itemOrder;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "matched_food_catalog_id")
    private Long matchedFoodCatalogId;

    @Column(name = "estimated_serving_g", nullable = false)
    private Double estimatedServingG;

    private Double calories;
    @Column(name = "protein_g")
    private Double proteinG;
    @Column(name = "carbs_g")
    private Double carbsG;
    @Column(name = "fat_g")
    private Double fatG;
    private Double confidence;

    @Column(name = "needs_review", nullable = false)
    @Builder.Default
    private boolean needsReview = true;

    @Column(name = "unknown_or_uncertain", columnDefinition = "TEXT")
    private String unknownOrUncertain;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
