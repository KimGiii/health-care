package com.healthcare.domain.diet.restriction.entity;

import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "diet_restrictions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DietRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "restriction_type", nullable = false, length = 10)
    private RestrictionType restrictionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "food_catalog_id")
    private Long foodCatalogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private FoodCategory category;

    @Column(name = "keyword", length = 100)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "allergen_tag", length = 30)
    private AllergenTag allergenTag;

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

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public enum RestrictionType {
        ALLERGY, AVOID
    }

    public enum TargetType {
        FOOD, CATEGORY, KEYWORD, ALLERGEN_TAG
    }
}
