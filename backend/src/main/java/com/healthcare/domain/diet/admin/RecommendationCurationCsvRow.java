package com.healthcare.domain.diet.admin;

import lombok.Builder;

@Builder
public record RecommendationCurationCsvRow(
        long rowNumber,
        String source,
        String foodCode,
        String recommendationStatus,
        String recommendationReason,
        String lastVerifiedAt,
        String reviewSourceUrl,
        String allergenTags,
        String allergenProfileVerified
) {
    static final String[] HEADERS = {
            "source", "food_code", "recommendation_status", "recommendation_reason",
            "last_verified_at", "review_source_url", "allergen_tags", "allergen_profile_verified"
    };
}
