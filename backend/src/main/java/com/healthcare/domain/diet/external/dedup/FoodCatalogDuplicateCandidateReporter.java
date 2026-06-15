package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.identity.FoodCatalogIdentity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FoodCatalogDuplicateCandidateReporter {

    public FoodCatalogDuplicateCandidateReport report(List<FoodCatalog> catalogs) {
        Map<String, List<FoodCatalog>> byNormalizedKey = catalogs.stream()
                .collect(Collectors.groupingBy(FoodCatalogIdentity::duplicateKey));

        List<FoodCatalogDuplicateGroup> groups = byNormalizedKey.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(e -> FoodCatalogDuplicateGroup.of(e.getKey(), e.getValue()))
                .sorted(Comparators.byGroupSizeDescThenKey())
                .toList();

        return new FoodCatalogDuplicateCandidateReport(groups);
    }

    private static final class Comparators {
        static java.util.Comparator<FoodCatalogDuplicateGroup> byGroupSizeDescThenKey() {
            return java.util.Comparator
                    .comparingInt((FoodCatalogDuplicateGroup g) -> g.entries().size()).reversed()
                    .thenComparing(FoodCatalogDuplicateGroup::normalizedKey);
        }
    }
}
