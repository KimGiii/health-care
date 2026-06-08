package com.healthcare.domain.diet.restriction.dto;

import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDietRestrictionRequest {

    @NotNull
    private RestrictionType restrictionType;

    @NotNull
    private TargetType targetType;

    private Long foodCatalogId;
    private FoodCategory category;
    private String keyword;
    private AllergenTag allergenTag;
}
