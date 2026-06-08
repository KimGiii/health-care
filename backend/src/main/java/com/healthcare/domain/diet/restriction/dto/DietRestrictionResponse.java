package com.healthcare.domain.diet.restriction.dto;

import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;

import java.time.OffsetDateTime;

public record DietRestrictionResponse(
        Long id,
        RestrictionType restrictionType,
        TargetType targetType,
        Long foodCatalogId,
        FoodCategory category,
        String keyword,
        AllergenTag allergenTag,
        OffsetDateTime createdAt
) {
    public static DietRestrictionResponse from(DietRestriction r) {
        return new DietRestrictionResponse(
                r.getId(),
                r.getRestrictionType(),
                r.getTargetType(),
                r.getFoodCatalogId(),
                r.getCategory(),
                r.getKeyword(),
                r.getAllergenTag(),
                r.getCreatedAt()
        );
    }
}
