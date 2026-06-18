-- V32: 검색 보조 컬럼(search_alias) 추가
-- MFDS 원본명(대분류_세분류, 예: 경단_깨)과 정규화 표시명(깨경단)을 함께 담아
-- 사용자가 어느 어순으로 검색해도 매칭되도록 한다.
-- 기존 행 백필은 관리자 재정규화 엔드포인트(POST /api/v1/admin/diet/catalog/renormalize-names)로 수행한다.
ALTER TABLE food_catalog
    ADD COLUMN IF NOT EXISTS search_alias VARCHAR(400);
