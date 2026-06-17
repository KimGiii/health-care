package com.healthcare.domain.diet.migration;

import com.healthcare.domain.diet.allergen.AllergenTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V25/V26/V27/V30 알러젠 seed 커버리지 리포트")
class AllergenSeedCoverageMigrationTest {

    private static final Pattern FOOD_SEED_ROW =
            Pattern.compile("\\('((?:[^']|'')*)',\\s*'((?:[^']|'')*)',\\s*'([A-Z_]+)'");
    private static final Pattern ALLOWLIST_ROW =
            Pattern.compile("\\('((?:[^']|'')*)',\\s*'([A-Z_]+)'\\)");
    private static final Pattern ALLERGEN_TAG_SELECT =
            Pattern.compile("SELECT id, '([A-Z_]+)'");
    private static final Pattern DELETE_ALLERGEN_TAG =
            Pattern.compile("fat\\.allergen_tag\\s*=\\s*'([A-Z_]+)'");
    private static final Pattern DELETE_FOOD_NAME =
            Pattern.compile("fc\\.name\\s*=\\s*'([^']+)'");
    private static final Pattern QUOTED_VALUE =
            Pattern.compile("'([^']+)'");

    @Test
    @DisplayName("P1 보강 후 seed는 추천 allowlist 42개 중 18개에 포함 태그를 제공한다")
    void p1Coverage_matchesCurrentReport() throws Exception {
        Map<SeedFoodKey, String> seedFoods = readSeedFoods();
        List<SeedFoodKey> allowlist = readAllowlist();
        Map<String, Set<String>> tagsByFoodName = readAllergenTagsByFoodName();
        Set<String> strictVerifiedFoodNames = readStrictVerifiedFoodNames();

        assertThat(allowlist).hasSize(42);
        assertThat(allowlist)
                .allSatisfy(key -> assertThat(seedFoods).containsKey(key));

        List<AllowlistCoverage> coverage = allowlist.stream()
                .map(key -> {
                    String name = seedFoods.get(key);
                    Set<String> tags = tagsByFoodName.getOrDefault(name, Set.of());
                    return new AllowlistCoverage(key, name, tags, strictVerifiedFoodNames.contains(name));
                })
                .toList();

        assertThat(coverage.stream().filter(row -> !row.tags().isEmpty()).count()).isEqualTo(18);
        assertThat(coverage.stream().filter(AllowlistCoverage::strictVerified).count()).isEqualTo(10);

        assertThat(rowByKoreanName(coverage, "토마토").tags()).containsExactly("TOMATO");
        assertThat(rowByKoreanName(coverage, "토마토").strictVerified()).isTrue();
        assertThat(rowByKoreanName(coverage, "두부").tags()).containsExactly("SOY");
        assertThat(rowByKoreanName(coverage, "두부").strictVerified()).isFalse();
        assertThat(rowByKoreanName(coverage, "콩나물").tags()).containsExactly("SOY");
        assertThat(rowByKoreanName(coverage, "콩나물").strictVerified()).isTrue();
        assertThat(rowByKoreanName(coverage, "통밀빵").tags()).containsExactlyInAnyOrder("GLUTEN", "WHEAT");
        assertThat(rowByKoreanName(coverage, "통밀빵").strictVerified()).isFalse();
        assertThat(rowByKoreanName(coverage, "오트밀").tags()).containsExactly("GLUTEN");
        assertThat(rowByKoreanName(coverage, "보리").tags()).containsExactly("GLUTEN");
        assertThat(rowByKoreanName(coverage, "메밀").tags()).containsExactly("BUCKWHEAT");
        assertThat(rowByKoreanName(coverage, "닭가슴살").tags()).containsExactly("CHICKEN");
        assertThat(rowByKoreanName(coverage, "우유(저지방)").strictVerified()).isTrue();
    }

    @Test
    @DisplayName("P1 보강 후 seed row가 없는 태그는 SULFITE만 남는다")
    void p1MissingTags_areCurrentKnownGaps() throws Exception {
        Set<String> seededTags = readAllergenTagsByFoodName().values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> allTags = Arrays.stream(AllergenTag.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));

        allTags.removeAll(seededTags);

        assertThat(allTags).containsExactly("SULFITE");
    }

    private AllowlistCoverage rowByKoreanName(List<AllowlistCoverage> coverage, String koreanName) {
        return coverage.stream()
                .filter(row -> row.key().koreanName().equals(koreanName))
                .findFirst()
                .orElseThrow();
    }

    private Map<SeedFoodKey, String> readSeedFoods() throws Exception {
        Map<SeedFoodKey, String> rows = new LinkedHashMap<>();
        for (String migration : List.of("V4__diet_schema.sql", "V12__food_catalog_seed_extended.sql")) {
            var matcher = FOOD_SEED_ROW.matcher(readMigration(migration));
            while (matcher.find()) {
                String name = unescapeSql(matcher.group(1));
                String koreanName = unescapeSql(matcher.group(2));
                String category = matcher.group(3);
                rows.put(new SeedFoodKey(koreanName, category), name);
            }
        }
        return rows;
    }

    private List<SeedFoodKey> readAllowlist() throws Exception {
        var matcher = ALLOWLIST_ROW.matcher(readMigration("V25__seed_recommendation_curation.sql"));
        List<SeedFoodKey> rows = new ArrayList<>();
        while (matcher.find()) {
            rows.add(new SeedFoodKey(unescapeSql(matcher.group(1)), matcher.group(2)));
        }
        return rows;
    }

    private Map<String, Set<String>> readAllergenTagsByFoodName() throws Exception {
        Map<String, Set<String>> tagsByFoodName = new TreeMap<>();
        for (String migration : List.of(
                "V26__seed_allergen_tags.sql",
                "V30__seed_allergen_tags_p1_coverage.sql"
        )) {
            String sql = readMigration(migration);
            for (String block : sql.split("ON CONFLICT \\(food_catalog_id, allergen_tag\\) DO NOTHING;")) {
                applyDeleteBlock(block, tagsByFoodName);
                var tagMatcher = ALLERGEN_TAG_SELECT.matcher(block);
                if (!tagMatcher.find()) {
                    continue;
                }
                String tag = tagMatcher.group(1);
                int whereIndex = block.indexOf("FROM food_catalog WHERE name");
                if (whereIndex < 0) {
                    continue;
                }
                String whereClause = block.substring(whereIndex);
                var nameMatcher = QUOTED_VALUE.matcher(whereClause);
                while (nameMatcher.find()) {
                    tagsByFoodName.computeIfAbsent(nameMatcher.group(1), ignored -> new TreeSet<>()).add(tag);
                }
            }
        }
        return tagsByFoodName;
    }

    private void applyDeleteBlock(String block, Map<String, Set<String>> tagsByFoodName) {
        if (!block.contains("DELETE FROM food_allergen_tags")) {
            return;
        }

        var tagMatcher = DELETE_ALLERGEN_TAG.matcher(block);
        var foodMatcher = DELETE_FOOD_NAME.matcher(block);
        if (!tagMatcher.find() || !foodMatcher.find()) {
            return;
        }

        String tag = tagMatcher.group(1);
        String foodName = foodMatcher.group(1);
        Set<String> tags = tagsByFoodName.get(foodName);
        if (tags != null) {
            tags.remove(tag);
        }
    }

    private Set<String> readStrictVerifiedFoodNames() throws Exception {
        Set<String> names = new TreeSet<>();
        for (String migration : List.of(
                "V27__allergen_profile_verified.sql",
                "V30__seed_allergen_tags_p1_coverage.sql"
        )) {
            String sql = readMigration(migration);
            int cursor = 0;
            while (true) {
                int start = sql.indexOf("fc.name IN (", cursor);
                if (start < 0) {
                    break;
                }
                int end = sql.indexOf(");", start);
                assertThat(end).isGreaterThan(start);

                var matcher = QUOTED_VALUE.matcher(sql.substring(start, end));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
                cursor = end + 2;
            }
        }
        return names;
    }

    private String readMigration(String fileName) throws Exception {
        URL resource = getClass().getClassLoader().getResource("db/migration/" + fileName);
        assertThat(resource).isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }

    private String unescapeSql(String value) {
        return value.replace("''", "'");
    }

    private record SeedFoodKey(String koreanName, String category) {
    }

    private record AllowlistCoverage(
            SeedFoodKey key,
            String foodName,
            Set<String> tags,
            boolean strictVerified
    ) {
    }
}
