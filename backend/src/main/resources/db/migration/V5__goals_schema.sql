-- ─────────────────────────── goals ───────────────────────────
CREATE TABLE goals (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_type           VARCHAR(30)     NOT NULL CHECK (goal_type IN (
                            'WEIGHT_LOSS', 'MUSCLE_GAIN', 'BODY_RECOMPOSITION', 'ENDURANCE', 'GENERAL_HEALTH'
                        )),
    target_value        DECIMAL(7,2),
    target_unit         VARCHAR(20),
    target_date         DATE,
    start_value         DECIMAL(7,2),
    start_date          DATE            NOT NULL DEFAULT CURRENT_DATE,
    status              VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED')),
    calorie_target      INTEGER,
    protein_target_g    INTEGER,
    carb_target_g       INTEGER,
    fat_target_g        INTEGER,
    weekly_rate_target  DECIMAL(4,2),
    completed_at        TIMESTAMPTZ,
    abandoned_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- 사용자당 ACTIVE 목표 1개 제한
CREATE UNIQUE INDEX uq_goals_user_active
    ON goals (user_id) WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX idx_goals_user
    ON goals (user_id, created_at DESC) WHERE deleted_at IS NULL;

-- ─────────────────────────── goal_checkpoints ───────────────────────────
CREATE TABLE goal_checkpoints (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goal_id             BIGINT          NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    checkpoint_date     DATE            NOT NULL,
    actual_value        DECIMAL(7,2),
    projected_value     DECIMAL(7,2),
    is_on_track         BOOLEAN,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goal_checkpoints_goal_date
    ON goal_checkpoints (goal_id, checkpoint_date);

CREATE UNIQUE INDEX uq_goal_checkpoints_weekly
    ON goal_checkpoints (goal_id, checkpoint_date);
