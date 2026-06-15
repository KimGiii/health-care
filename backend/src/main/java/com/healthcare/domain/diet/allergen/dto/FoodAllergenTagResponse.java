package com.healthcare.domain.diet.allergen.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;

import java.time.OffsetDateTime;

public record FoodAllergenTagResponse(
        Long id,
        Long foodCatalogId,
        AllergenTag allergenTag,
        AllergenConfidenceLevel confidenceLevel,
        AllergenDataSource source,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt
) {
    public static FoodAllergenTagResponse from(FoodAllergenTag tag) {
        return new FoodAllergenTagResponse(
                tag.getId(),
                tag.getFoodCatalogId(),
                tag.getAllergenTag(),
                tag.getConfidenceLevel(),
                tag.getSource(),
                tag.getReviewedAt(),
                tag.getCreatedAt()
        );
    }
}
