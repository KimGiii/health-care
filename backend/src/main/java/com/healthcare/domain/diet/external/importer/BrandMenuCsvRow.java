package com.healthcare.domain.diet.external.importer;

import lombok.Builder;

@Builder
public record BrandMenuCsvRow(
        String brandName,
        String menuName,
        String category,
        String servingSizeG,
        String calories,
        String protein,
        String carbs,
        String fat,
        String sodium,
        String sugar,
        String saturatedFat,
        String sourceUrl,
        String lastVerifiedAt,
        String recommendationStatus,
        String recommendationReason
) {
    static final String[] HEADERS = {
            "brand_name", "menu_name", "category",
            "serving_size_g", "calories", "protein", "carbs", "fat",
            "sodium", "sugar", "saturated_fat",
            "source_url", "last_verified_at",
            "recommendation_status", "recommendation_reason"
    };
}
