-- Phase 4: 식단 기록과 추천 스냅샷을 연결해 기록 전환 funnel을 추적한다.

ALTER TABLE diet_logs
    ADD COLUMN recommendation_snapshot_id BIGINT
        REFERENCES recommendation_snapshots(id) ON DELETE SET NULL;

CREATE INDEX idx_diet_logs_snapshot
    ON diet_logs(recommendation_snapshot_id)
    WHERE recommendation_snapshot_id IS NOT NULL;
