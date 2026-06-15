package com.healthcare.domain.diet.allergen.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;

import java.time.OffsetDateTime;

public record AddAllergenTagRequest(
        Long foodCatalogId,
        AllergenTag allergenTag,
        AllergenConfidenceLevel confidenceLevel,
        AllergenDataSource source,
        OffsetDateTime reviewedAt
) {}
