package com.healthcare.domain.diet.allergen.dto;

import java.util.List;

public record BulkAddAllergenTagsRequest(List<AddAllergenTagRequest> tags) {}
