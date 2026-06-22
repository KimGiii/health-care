-- 출처 우선순위 dedup: 출처를 가로지르는 동일 식품을 정규(canonical) 1개로 수렴시킨다(설계 §3).
-- canonical_group_id(V35)를 supersede 포인터로 재사용한다(NULL = 그룹 대표 = 추천/검색 노출).
-- 신규 컬럼은 클러스터 키(dedup_group=food_code, dedup_name_key)와 상태(dedup_state)뿐이다.
-- 기본값 CANONICAL → 기존 행 무영향. 실제 canonical 수렴은 별도 백필/재적재에서 수행한다(전량 적재는 보류).

ALTER TABLE food_catalog
    ADD COLUMN dedup_group    VARCHAR(60),
    ADD COLUMN dedup_name_key VARCHAR(160),
    ADD COLUMN dedup_state    VARCHAR(20) NOT NULL DEFAULT 'CANONICAL';

ALTER TABLE food_catalog
    ADD CONSTRAINT chk_food_catalog_dedup_state CHECK (
        dedup_state IN ('CANONICAL', 'SUPERSEDED', 'COLLISION')
    );

-- 클러스터 조회용(코드 보유·미삭제 행만).
CREATE INDEX ix_food_catalog_dedup_group
    ON food_catalog (dedup_group)
    WHERE deleted_at IS NULL AND dedup_group IS NOT NULL;

-- (코드, 이름클러스터)당 대표 1개 강제. 대표 = canonical_group_id IS NULL.
-- 충돌(COLLISION)은 이름키가 다르므로 각 클러스터가 각자 대표를 가질 수 있어 위배되지 않는다.
CREATE UNIQUE INDEX uq_food_catalog_canonical
    ON food_catalog (dedup_group, dedup_name_key)
    WHERE canonical_group_id IS NULL AND deleted_at IS NULL AND dedup_group IS NOT NULL;
