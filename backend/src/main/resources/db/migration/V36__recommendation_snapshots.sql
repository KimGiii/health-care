-- Phase 4: 추천 스냅샷과 이벤트를 저장해 노출→재추천→기록전환 funnel을 추적한다.

CREATE TABLE recommendation_snapshots (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    snapshot_date        DATE        NOT NULL,
    meals_json           TEXT        NOT NULL,
    goal_type            VARCHAR(30),
    strict_allergy_mode  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recommendation_snapshots_user_date
    ON recommendation_snapshots(user_id, snapshot_date);

CREATE TABLE recommendation_events (
    id               BIGSERIAL PRIMARY KEY,
    snapshot_id      BIGINT      NOT NULL REFERENCES recommendation_snapshots(id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL,
    event_type       VARCHAR(20) NOT NULL,
    feedback_reason  VARCHAR(50),
    diet_log_id      BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_recommendation_event_type
        CHECK (event_type IN ('GENERATED', 'EXPOSED', 'REFRESHED', 'RECORDED')),
    CONSTRAINT chk_recommendation_event_reason
        CHECK (
            (event_type = 'REFRESHED' AND feedback_reason IS NOT NULL)
            OR (event_type <> 'REFRESHED')
        )
);

CREATE INDEX idx_recommendation_events_snapshot
    ON recommendation_events(snapshot_id);

CREATE INDEX idx_recommendation_events_user
    ON recommendation_events(user_id, created_at);
