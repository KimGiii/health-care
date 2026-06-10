package com.healthcare.domain.diet.external.dedup;

import java.util.List;

public record FoodCatalogDuplicateCandidateReport(
        List<FoodCatalogDuplicateGroup> groups
) {
    public int totalGroups() {
        return groups.size();
    }

    public int totalCandidates() {
        return groups.stream().mapToInt(g -> g.entries().size()).sum();
    }
}
