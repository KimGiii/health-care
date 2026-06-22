package com.healthcare.domain.diet.allergen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AllergenDataSource enum과 Flyway 마이그레이션의 DB CHECK 제약 값이 일치하는지 검증한다.
 *
 * <p>새 값을 enum에 추가하면 반드시 Flyway 마이그레이션도 함께 수정해야 한다.
 * 이 테스트는 그 규칙을 코드 레벨에서 강제해 CI가 잡도록 한다.
 *
 * <p>DB 연결이 필요없고 Flyway SQL 파일을 classpath에서 직접 읽어 비교한다.
 */
@DisplayName("AllergenDataSource enum ↔ DB CHECK 제약 동기화")
class AllergenDataSourceSchemaConsistencyTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__");
    private static final Pattern CHECK_SOURCE_IN_PATTERN = Pattern.compile(
            "CHECK\\s*\\(\\s*source\\s+IN\\s*\\((.*?)\\)\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("'([^']+)'");

    @Test
    @DisplayName("AllergenDataSource 모든 값이 최신 마이그레이션 CHECK 제약에 포함된다")
    void enumValuesCoveredByMigrationCheckConstraint() throws Exception {
        Set<String> constraintValues = extractSourceCheckValues();
        Set<String> enumValues = enumNames();

        assertThat(constraintValues)
                .as("""
                        다음 AllergenDataSource 값이 food_allergen_tags.source CHECK 제약에 없습니다.
                        Flyway 마이그레이션을 추가하거나 기존 제약을 수정하세요.
                        누락 값: %s""",
                        enumValues.stream()
                                .filter(v -> !constraintValues.contains(v))
                                .collect(Collectors.toSet()))
                .containsAll(enumValues);
    }

    @Test
    @DisplayName("DB CHECK 제약 값이 AllergenDataSource enum과 정확히 일치한다")
    void migrationCheckConstraintMatchesEnumExactly() throws Exception {
        Set<String> constraintValues = extractSourceCheckValues();
        Set<String> enumValues = enumNames();

        assertThat(constraintValues)
                .as("""
                        food_allergen_tags.source CHECK 제약과 AllergenDataSource enum 값 목록이 다릅니다.
                        CHECK 전용 잉여 값: %s
                        enum 전용 누락 값: %s""",
                        constraintValues.stream().filter(v -> !enumValues.contains(v)).collect(Collectors.toSet()),
                        enumValues.stream().filter(v -> !constraintValues.contains(v)).collect(Collectors.toSet()))
                .isEqualTo(enumValues);
    }

    // -----------------------------------------------------------------------

    private Set<String> enumNames() {
        return Arrays.stream(AllergenDataSource.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> extractSourceCheckValues() throws IOException, URISyntaxException {
        List<Path> migrations = loadMigrationsInVersionOrder();
        String lastSql = findLastMigrationContaining(migrations, "food_allergen_tags_source_check");
        return parseInListValues(lastSql);
    }

    private List<Path> loadMigrationsInVersionOrder() throws IOException, URISyntaxException {
        URL url = getClass().getClassLoader().getResource("db/migration");
        assertThat(url).as("classpath:db/migration 디렉터리를 찾을 수 없습니다").isNotNull();
        try (Stream<Path> stream = Files.list(Paths.get(url.toURI()))) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(java.util.Comparator.comparingInt(p -> versionOf(p.getFileName().toString())))
                    .collect(Collectors.toList());
        }
    }

    private int versionOf(String filename) {
        Matcher m = VERSION_PATTERN.matcher(filename);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private String findLastMigrationContaining(List<Path> migrations, String marker) throws IOException {
        String lastMatch = null;
        for (Path file : migrations) {
            String content = Files.readString(file);
            if (content.contains(marker)) {
                lastMatch = content;
            }
        }
        assertThat(lastMatch)
                .as("'%s' 를 포함하는 Flyway 마이그레이션을 찾을 수 없습니다", marker)
                .isNotNull();
        return lastMatch;
    }

    private Set<String> parseInListValues(String sql) {
        Matcher matcher = CHECK_SOURCE_IN_PATTERN.matcher(sql);
        assertThat(matcher.find())
                .as("SQL 에서 CHECK (source IN (...)) 패턴을 파싱할 수 없습니다")
                .isTrue();

        Set<String> values = new LinkedHashSet<>();
        Matcher valueMatcher = QUOTED_VALUE_PATTERN.matcher(matcher.group(1));
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }
        return values;
    }
}
