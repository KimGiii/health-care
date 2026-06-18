-- food_serving_options: 식품별 이산 제공량 옵션
-- sort_order=0이 기본(primary) 옵션. equivalents_g는 영양 계산에 사용한다.
CREATE TABLE food_serving_options (
    id             BIGSERIAL PRIMARY KEY,
    food_catalog_id BIGINT NOT NULL REFERENCES food_catalog(id),
    label          VARCHAR(60) NOT NULL,         -- e.g. "1회 제공량", "1개", "100g"
    label_ko       VARCHAR(60),                  -- 한국어 표시명 (선택)
    equivalent_g   DOUBLE PRECISION NOT NULL,    -- 환산 g
    sort_order     SMALLINT NOT NULL DEFAULT 0,  -- 0 = 기본 옵션, 오름차순 정렬
    serving_type   VARCHAR(30) NOT NULL,         -- OFFICIAL_SERVING | COUNT_UNIT | WEIGHT_STEP
    verified_at    TIMESTAMPTZ,                  -- NULL = 미검증
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_food_serving_options_food_id ON food_serving_options(food_catalog_id);
