package com.healthcare.domain.diet.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V36 추천 스냅샷·이벤트 migration")
class RecommendationSnapshotMigrationTest {

    @Test
    @DisplayName("recommendation_snapshots 테이블은 user_id·snapshot_date·meals_json을 가진다")
    void createsRecommendationSnapshotsTable() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("CREATE TABLE recommendation_snapshots");
        assertThat(sql).containsPattern("user_id\\s+BIGINT");
        assertThat(sql).contains("REFERENCES users(id)");
        assertThat(sql).contains("snapshot_date");
        assertThat(sql).contains("meals_json");
    }

    @Test
    @DisplayName("recommendation_events 테이블은 snapshot_id FK와 event_type CHECK 제약을 가진다")
    void createsRecommendationEventsTable() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("CREATE TABLE recommendation_events");
        assertThat(sql).containsPattern("snapshot_id\\s+BIGINT");
        assertThat(sql).contains("REFERENCES recommendation_snapshots(id)");
        assertThat(sql).contains("event_type");
        assertThat(sql).contains("'GENERATED'");
        assertThat(sql).contains("'EXPOSED'");
        assertThat(sql).contains("'REFRESHED'");
        assertThat(sql).contains("'RECORDED'");
    }

    @Test
    @DisplayName("90일 초과 스냅샷 삭제에 사용할 created_at 인덱스가 있다")
    void hasCreatedAtIndexForRetentionQuery() throws Exception {
        String sql = readMigration();

        assertThat(sql).contains("idx_recommendation_events_user");
    }

    private String readMigration() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("db/migration/V36__recommendation_snapshots.sql");
        assertThat(resource).isNotNull();
        return Files.readString(Path.of(resource.toURI()));
    }
}
