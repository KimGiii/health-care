-- #85 선행 계측: 대안 식단 swap(applyAlternative)을 SWAPPED 이벤트로 기록한다.
-- A/B/C 설계 판정에 필요한 swap 빈도 데이터가 현재 0(계측 부재)이므로, 스냅샷 스키마 확장 전에
-- 최소 계측만 먼저 넣는다. alternative_index는 어느 대안이 선택됐는지(B안 activeSolutionIndex
-- 판정 데이터)를 함께 남긴다. SWAPPED는 스냅샷 단위 이벤트라 food_catalog_id는 null이다
-- (대안 식품 목록은 스냅샷에 없어 food fan-out이 불가능하다 — 그게 바로 #85 본 이슈).

ALTER TABLE recommendation_events
    DROP CONSTRAINT chk_recommendation_event_type;

ALTER TABLE recommendation_events
    ADD CONSTRAINT chk_recommendation_event_type
        CHECK (event_type IN ('GENERATED', 'EXPOSED', 'REFRESHED', 'RECORDED', 'SWAPPED'));

-- INTEGER인 이유: 엔티티 필드가 Integer라 ddl-auto=validate가 SMALLINT(int2)를 거부한다.
ALTER TABLE recommendation_events
    ADD COLUMN alternative_index INTEGER;
