-- canonical 식품 그룹 지원: 동일 식품의 중복 행 중 대표 후보를 지정한다.
-- canonical_group_id IS NULL  = 독립 식품이거나 그룹 대표 → 추천 후보 포함
-- canonical_group_id IS NOT NULL = 비대표(중복) → 추천에서 제외, 검색에서만 노출
ALTER TABLE food_catalog
    ADD COLUMN IF NOT EXISTS canonical_group_id BIGINT
        REFERENCES food_catalog (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_food_catalog_canonical_group_id
    ON food_catalog (canonical_group_id)
    WHERE canonical_group_id IS NOT NULL;
