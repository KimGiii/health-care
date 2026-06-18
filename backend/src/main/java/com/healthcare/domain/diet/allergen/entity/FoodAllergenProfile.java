package com.healthcare.domain.diet.allergen.entity;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "food_allergen_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class FoodAllergenProfile {

    @Id
    @Column(name = "food_catalog_id")
    private Long foodCatalogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 20)
    private AllergenConfidenceLevel confidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private AllergenDataSource source;

    @Column(name = "standard_version", nullable = false, length = 80)
    private String standardVersion;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "invalidated_at")
    private OffsetDateTime invalidatedAt;

    @Column(name = "invalidation_reason", length = 255)
    private String invalidationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isVerifiedAt(OffsetDateTime referenceTime) {
        boolean highConfidence = confidenceLevel == AllergenConfidenceLevel.DIRECT_VERIFIED
                || confidenceLevel == AllergenConfidenceLevel.LABEL_DERIVED;
        boolean reviewStarted = reviewedAt != null && !reviewedAt.isAfter(referenceTime);
        boolean notExpired = validUntil == null || validUntil.isAfter(referenceTime);
        return highConfidence && reviewStarted && notExpired && invalidatedAt == null;
    }
}
