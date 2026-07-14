package com.healthcare.domain.diet.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V40 추천 이벤트 food 매핑 migration")
class RecommendationEventFoodMappingMigrationTest {

    @Test
    @DisplayName("recommendation_events에 food_catalog_id 컬럼을 추가한다")
    void addsFoodCatalogIdColumn() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("ALTER TABLE recommendation_events");
        assertThat(sql).containsPattern("food_catalog_id\\s+BIGINT");
    }

    private String readMigration() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("db/migration/V40__recommendation_event_food_mapping.sql");
        assertThat(resource).as("V40 migration 파일 존재").isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }
}
