-- R1: 추천 이벤트를 식품 단위로 귀속시켜 전환율·재추천 사유·온라인 튜닝 KPI 측정을 가능하게 한다.
-- GENERATED/REFRESHED/RECORDED 이벤트를 스냅샷의 식품별로 fan-out 저장한다.
-- FK는 두지 않는다: user_id와 동일하게 이벤트 로그 성격이며 90일 보관 정책이 정리한다.
-- 집계 쿼리는 기존 idx_recommendation_events_user(user_id, created_at)로 커버된다(사용자당 행 수 소량).

ALTER TABLE recommendation_events
    ADD COLUMN food_catalog_id BIGINT;
