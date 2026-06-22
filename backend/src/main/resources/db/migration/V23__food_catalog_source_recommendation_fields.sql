-- food_catalog 출처/브랜드/제공량/추천 상태 메타데이터
-- 기존 시드 식품은 추천 후보를 유지하고, 사용자 커스텀 식품은 기본 검색 전용으로 둔다.

ALTER TABLE food_catalog
    ADD COLUMN food_code             VARCHAR(60),
    ADD COLUMN source                VARCHAR(40),
    ADD COLUMN source_detail         VARCHAR(120),
    ADD COLUMN brand_name            VARCHAR(150),
    ADD COLUMN maker                 VARCHAR(150),
    ADD COLUMN serving_size_g        DOUBLE PRECISION,
    ADD COLUMN serving_reference     VARCHAR(80),
    ADD COLUMN recommendation_status VARCHAR(40),
    ADD COLUMN recommendation_reason VARCHAR(255),
    ADD COLUMN data_version          VARCHAR(80),
    ADD COLUMN last_verified_at      TIMESTAMPTZ;

UPDATE food_catalog
SET source = CASE
        WHEN is_custom = TRUE THEN 'USER_CUSTOM'
        ELSE 'SEED'
    END
WHERE source IS NULL;

UPDATE food_catalog
SET recommendation_status = CASE
        WHEN is_custom = TRUE THEN 'SEARCH_ONLY'
        ELSE 'RECOMMENDABLE'
    END
WHERE recommendation_status IS NULL;

ALTER TABLE food_catalog
    ALTER COLUMN source SET NOT NULL,
    ALTER COLUMN source SET DEFAULT 'SEED',
    ALTER COLUMN recommendation_status SET NOT NULL,
    ALTER COLUMN recommendation_status SET DEFAULT 'SEARCH_ONLY',
    ADD CONSTRAINT chk_food_catalog_source CHECK (
        source IN (
            'SEED',
            'MFDS_STANDARD_PROCESSED',
            'MFDS_STANDARD_DISH',
            'MFDS_FOOD_NUTRIENT_DB',
            'BRAND_OFFICIAL',
            'USER_CUSTOM'
        )
    ),
    ADD CONSTRAINT chk_food_catalog_recommendation_status CHECK (
        recommendation_status IN (
            'SEARCH_ONLY',
            'RECOMMENDABLE',
            'RECOMMENDABLE_WITH_CAUTION',
            'DISABLED'
        )
    );

CREATE UNIQUE INDEX uq_food_catalog_source_food_code
    ON food_catalog (source, food_code)
    WHERE food_code IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_food_catalog_recommendation_status
    ON food_catalog (recommendation_status)
    WHERE deleted_at IS NULL;
