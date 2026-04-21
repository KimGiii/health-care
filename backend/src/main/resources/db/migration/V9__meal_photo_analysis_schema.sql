CREATE TABLE meal_photo_analyses (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_key         VARCHAR(512)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    file_size_bytes     BIGINT        NOT NULL,
    captured_at         TIMESTAMPTZ   NOT NULL,
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('INITIATED', 'ANALYZED', 'FAILED', 'CONFIRMED')),
    provider            VARCHAR(50),
    analysis_version    VARCHAR(50),
    raw_model_output    TEXT,
    analysis_warnings   TEXT,
    confirmed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE meal_photo_analysis_items (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_id               BIGINT        NOT NULL REFERENCES meal_photo_analyses(id) ON DELETE CASCADE,
    item_order                INTEGER       NOT NULL,
    label                     VARCHAR(150)  NOT NULL,
    matched_food_catalog_id   BIGINT        REFERENCES food_catalog(id) ON DELETE SET NULL,
    estimated_serving_g       DOUBLE PRECISION NOT NULL,
    calories                  DOUBLE PRECISION,
    protein_g                 DOUBLE PRECISION,
    carbs_g                   DOUBLE PRECISION,
    fat_g                     DOUBLE PRECISION,
    confidence                DOUBLE PRECISION,
    needs_review              BOOLEAN       NOT NULL DEFAULT TRUE,
    unknown_or_uncertain      TEXT,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_meal_photo_analyses_storage_key
    ON meal_photo_analyses (storage_key);

CREATE INDEX idx_meal_photo_analyses_user_created
    ON meal_photo_analyses (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_meal_photo_analysis_items_analysis_order
    ON meal_photo_analysis_items (analysis_id, item_order ASC);
