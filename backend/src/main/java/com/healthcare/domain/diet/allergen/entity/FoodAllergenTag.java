package com.healthcare.domain.diet.allergen.entity;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "food_allergen_tags",
       uniqueConstraints = @UniqueConstraint(columnNames = {"food_catalog_id", "allergen_tag"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class FoodAllergenTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "food_catalog_id", nullable = false)
    private Long foodCatalogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allergen_tag", nullable = false, length = 30)
    private AllergenTag allergenTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 20)
    private AllergenConfidenceLevel confidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private AllergenDataSource source;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
