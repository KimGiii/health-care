CREATE TABLE progress_photos (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    photo_type          VARCHAR(20)   NOT NULL CHECK (photo_type IN ('FRONT', 'BACK', 'SIDE_LEFT', 'SIDE_RIGHT', 'DETAIL')),
    captured_at         TIMESTAMPTZ   NOT NULL,
    photo_date          DATE          NOT NULL,
    storage_key         VARCHAR(512)  NOT NULL,
    thumbnail_key_150   VARCHAR(512),
    thumbnail_key_400   VARCHAR(512),
    thumbnail_key_800   VARCHAR(512),
    content_type        VARCHAR(100),
    file_size_bytes     BIGINT,
    exif_stripped       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_private          BOOLEAN       NOT NULL DEFAULT TRUE,
    is_baseline         BOOLEAN       NOT NULL DEFAULT FALSE,
    body_weight_kg      DOUBLE PRECISION,
    body_fat_pct        DOUBLE PRECISION,
    waist_cm            DOUBLE PRECISION,
    notes               TEXT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_progress_photos_storage_key
    ON progress_photos (storage_key);

CREATE INDEX idx_progress_photos_user_type_date
    ON progress_photos (user_id, photo_type, photo_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_progress_photos_baseline
    ON progress_photos (user_id, photo_type)
    WHERE is_baseline = TRUE AND deleted_at IS NULL;
