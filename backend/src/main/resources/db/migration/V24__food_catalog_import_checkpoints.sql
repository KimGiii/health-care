-- 공공데이터 식품 카탈로그 페이지 적재 재시작 체크포인트

CREATE TABLE food_catalog_import_checkpoints (
    source              VARCHAR(40) PRIMARY KEY,
    last_completed_page INTEGER NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_food_catalog_import_checkpoint_source CHECK (
        source IN (
            'MFDS_STANDARD_PROCESSED',
            'MFDS_STANDARD_DISH',
            'MFDS_FOOD_NUTRIENT_DB'
        )
    ),
    CONSTRAINT chk_food_catalog_import_checkpoint_page CHECK (last_completed_page >= 0)
);
