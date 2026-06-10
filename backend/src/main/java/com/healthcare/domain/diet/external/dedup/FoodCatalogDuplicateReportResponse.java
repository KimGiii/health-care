package com.healthcare.domain.diet.external.dedup;

import java.util.List;

public record FoodCatalogDuplicateReportResponse(
        int totalGroups,
        int totalCandidates,
        List<FoodCatalogDuplicateGroupResponse> groups
) {
    static FoodCatalogDuplicateReportResponse from(FoodCatalogDuplicateCandidateReport report) {
        List<FoodCatalogDuplicateGroupResponse> groups = report.groups().stream()
                .map(FoodCatalogDuplicateGroupResponse::from)
                .toList();
        return new FoodCatalogDuplicateReportResponse(report.totalGroups(), report.totalCandidates(), groups);
    }
}
