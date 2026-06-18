package com.healthcare.domain.diet.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V29 식단 제한 조건 활성 중복 방지 마이그레이션")
class DietRestrictionUniqueIndexMigrationTest {

    @Test
    @DisplayName("target type별 활성 제한 조건 부분 유니크 인덱스를 생성한다")
    void v29_dietRestrictionActiveUniqueIndexes_areScopedByTargetTypeAndActiveRows() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("CREATE UNIQUE INDEX uq_diet_restrictions_active_food");
        assertThat(sql).contains("ON diet_restrictions (user_id, restriction_type, food_catalog_id)");
        assertThat(sql).contains("target_type = 'FOOD'");
        assertThat(sql).contains("food_catalog_id IS NOT NULL");

        assertThat(sql).contains("CREATE UNIQUE INDEX uq_diet_restrictions_active_category");
        assertThat(sql).contains("ON diet_restrictions (user_id, restriction_type, category)");
        assertThat(sql).contains("target_type = 'CATEGORY'");
        assertThat(sql).contains("category IS NOT NULL");

        assertThat(sql).contains("CREATE UNIQUE INDEX uq_diet_restrictions_active_keyword");
        assertThat(sql).contains("ON diet_restrictions (user_id, restriction_type, keyword)");
        assertThat(sql).contains("target_type = 'KEYWORD'");
        assertThat(sql).contains("keyword IS NOT NULL");

        assertThat(sql).contains("CREATE UNIQUE INDEX uq_diet_restrictions_active_allergen_tag");
        assertThat(sql).contains("ON diet_restrictions (user_id, restriction_type, allergen_tag)");
        assertThat(sql).contains("target_type = 'ALLERGEN_TAG'");
        assertThat(sql).contains("allergen_tag IS NOT NULL");

        assertThat(sql).contains("WHERE deleted_at IS NULL");
    }

    @Test
    @DisplayName("인덱스 생성 전에 활성 중복 행을 soft-delete 로 정리한다")
    void v29_softDeletesActiveDuplicates_beforeCreatingUniqueIndexes() throws Exception {
        String sql = readMigration();

        // 정리 단계가 인덱스 생성보다 먼저 위치해야 한다.
        int firstCleanup = sql.indexOf("SET deleted_at = NOW()");
        int firstIndex = sql.indexOf("CREATE UNIQUE INDEX");
        assertThat(firstCleanup).isGreaterThanOrEqualTo(0);
        assertThat(firstIndex).isGreaterThan(firstCleanup);

        // target type 4종 각각에 대해 최신(id 최대) 1건만 남기는 정리 쿼리가 있어야 한다.
        assertThat(sql).contains("PARTITION BY user_id, restriction_type, food_catalog_id ORDER BY id DESC");
        assertThat(sql).contains("PARTITION BY user_id, restriction_type, category ORDER BY id DESC");
        assertThat(sql).contains("PARTITION BY user_id, restriction_type, keyword ORDER BY id DESC");
        assertThat(sql).contains("PARTITION BY user_id, restriction_type, allergen_tag ORDER BY id DESC");
        assertThat(sql).contains("rn > 1");
    }

    private String readMigration() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("db/migration/V29__diet_restrictions_active_unique_indexes.sql");
        assertThat(resource).isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }
}
