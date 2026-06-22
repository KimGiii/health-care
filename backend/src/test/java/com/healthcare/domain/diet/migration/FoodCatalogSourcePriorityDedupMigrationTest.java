package com.healthcare.domain.diet.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V39 출처 우선순위 dedup migration")
class FoodCatalogSourcePriorityDedupMigrationTest {

    @Test
    @DisplayName("dedup 클러스터 키·상태 컬럼을 추가한다")
    void addsDedupColumns() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("ADD COLUMN dedup_group");
        assertThat(sql).contains("ADD COLUMN dedup_name_key");
        assertThat(sql).contains("ADD COLUMN dedup_state");
        assertThat(sql).contains("DEFAULT 'CANONICAL'");
    }

    @Test
    @DisplayName("dedup_state는 CANONICAL/SUPERSEDED/COLLISION CHECK 제약을 가진다")
    void constrainsDedupState() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("chk_food_catalog_dedup_state");
        assertThat(sql).contains("'CANONICAL'");
        assertThat(sql).contains("'SUPERSEDED'");
        assertThat(sql).contains("'COLLISION'");
    }

    @Test
    @DisplayName("(코드, 이름키)당 대표 1개를 강제하는 부분 유니크 인덱스를 만든다")
    void enforcesCanonicalUniqueness() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("CREATE UNIQUE INDEX uq_food_catalog_canonical");
        assertThat(sql).contains("(dedup_group, dedup_name_key)");
        // 대표 = canonical_group_id IS NULL (V35 재사용)
        assertThat(sql).contains("canonical_group_id IS NULL");
    }

    @Test
    @DisplayName("코드 보유 행만 색인하는 그룹 조회 인덱스를 만든다")
    void createsGroupLookupIndex() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("CREATE INDEX ix_food_catalog_dedup_group");
        assertThat(sql).contains("dedup_group IS NOT NULL");
    }

    private String readMigration() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("db/migration/V39__food_catalog_source_priority_dedup.sql");
        assertThat(resource).isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }
}
