package com.healthcare.domain.diet.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V33 추천 데이터 계약 migration")
class RecommendationDataContractsMigrationTest {

    @Test
    @DisplayName("알러젠 프로필은 태그와 분리된 식품 단위 검토 레코드이며 기존 검증값을 백필한다")
    void createsSeparateAllergenProfilesAndBackfillsVerifiedTags() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("CREATE TABLE food_allergen_profiles");
        assertThat(sql).containsPattern("food_catalog_id\\s+BIGINT PRIMARY KEY");
        assertThat(sql).contains("REFERENCES food_catalog(id)");
        assertThat(sql).contains("INSERT INTO food_allergen_profiles");
        assertThat(sql).contains("allergen_profile_verified = TRUE");
    }

    private String readMigration() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("db/migration/V33__recommendation_data_contracts.sql");
        assertThat(resource).isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }
}
