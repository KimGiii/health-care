-- Body Measurement Domain
-- 신체 측정 기록 (체중, 체지방, 신체 부위 사이즈 등)

CREATE TABLE body_measurements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    measured_at     DATE          NOT NULL,

    -- 기본 측정값
    weight_kg       DOUBLE PRECISION,
    body_fat_pct    DOUBLE PRECISION,
    muscle_mass_kg  DOUBLE PRECISION,
    bmi             DOUBLE PRECISION,

    -- 신체 부위 사이즈 (cm)
    chest_cm        DOUBLE PRECISION,
    waist_cm        DOUBLE PRECISION,
    hip_cm          DOUBLE PRECISION,
    thigh_cm        DOUBLE PRECISION,
    arm_cm          DOUBLE PRECISION,

    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- 사용자별 날짜 내림차순 조회 (soft-delete 제외)
CREATE INDEX idx_body_measurements_user_date
    ON body_measurements (user_id, measured_at DESC)
    WHERE deleted_at IS NULL;

-- 날짜 범위 쿼리 보조
CREATE INDEX idx_body_measurements_user_date_range
    ON body_measurements (user_id, measured_at)
    WHERE deleted_at IS NULL;
