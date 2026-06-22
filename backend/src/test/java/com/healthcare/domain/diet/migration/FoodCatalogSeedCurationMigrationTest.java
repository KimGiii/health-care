package com.healthcare.domain.diet.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V25 seed 추천 큐레이션 보정 마이그레이션")
class FoodCatalogSeedCurationMigrationTest {

    private static final Pattern ALLOWLIST_ROW =
            Pattern.compile("\\('([^']+)',\\s*'([A-Z_]+)'\\)");

    @Test
    @DisplayName("seed 전체를 SEARCH_ONLY로 낮춘 뒤 명시 allowlist만 RECOMMENDABLE로 승격한다")
    void v25_seedRecommendationCuration_usesExplicitAllowlistOnly() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("V25: seed 추천 큐레이션 보정");
        assertThat(sql).contains("SET recommendation_status = 'SEARCH_ONLY'");
        assertThat(sql).contains("SET recommendation_status = 'RECOMMENDABLE'");
        assertThat(sql).contains("WITH seed_allowlist(name_ko, category) AS (");
        assertThat(sql).doesNotContain("RECOMMENDABLE_WITH_CAUTION");
        assertThat(sql).doesNotContain("food_code");

        var allowlist = ALLOWLIST_ROW.matcher(sql)
                .results()
                .map(match -> new SeedAllowlistRow(match.group(1), match.group(2)))
                .toList();

        assertThat(allowlist).hasSizeBetween(40, 60);
        assertThat(allowlist.stream()
                .collect(Collectors.groupingBy(SeedAllowlistRow::category, Collectors.counting())))
                .satisfies(counts -> {
                    assertThat(counts.getOrDefault("PROTEIN_SOURCE", 0L)).isGreaterThanOrEqualTo(10);
                    assertThat(counts.getOrDefault("GRAIN", 0L)).isGreaterThanOrEqualTo(8);
                    assertThat(counts.getOrDefault("VEGETABLE", 0L)).isGreaterThanOrEqualTo(10);
                    assertThat(counts.getOrDefault("FRUIT", 0L)).isGreaterThanOrEqualTo(6);
                    assertThat(counts.getOrDefault("DAIRY", 0L)).isGreaterThanOrEqualTo(4);
                });
    }

    private String readMigration() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("db/migration/V25__seed_recommendation_curation.sql");
        assertThat(resource).isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }

    private record SeedAllowlistRow(String nameKo, String category) {
    }
}
