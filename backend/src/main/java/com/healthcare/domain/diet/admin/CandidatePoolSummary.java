package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;

import java.util.List;
import java.util.Map;

public record CandidatePoolSummary(
        long totalCandidates,
        long macroCompleteTotal,
        long verifiedServingOptionTotal,
        long allergenProfileVerifiedTotal,
        long engineReadyTotal,
        long untaggedTotal,
        Map<FoodCategory, Long> countByCategory,
        List<FoodCategory> underrepresentedCategories
) {
    static final long UNDERREPRESENTED_THRESHOLD = 3;

    public long byCategoryCount(FoodCategory category) {
        return countByCategory.getOrDefault(category, 0L);
    }
}
