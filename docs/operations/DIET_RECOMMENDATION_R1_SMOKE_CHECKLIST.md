# 식단 추천 R1 배포 스모크 체크리스트

작성일: 2026-07-20
대상: dev 배포 직후 (PR #84 테스트 플랜의 미체크 항목 + PR #87 swap 계측 포함)
선행: dev 백엔드 배포 완료, Flyway V40·V41 적용 확인, 테스트 계정(프로필·영양 목표 설정 완료) 준비
관련: 이슈 #90, `DIET_RECOMMENDATION_BETA_TESTER_RECRUITMENT.md` §4

목적: 관찰 시계를 시작하기 전에 **이벤트 적재 파이프라인이 실제 환경에서 동작하는지** 검증한다. 이 릴리스의 가치는 "2주 뒤 읽을 데이터"이므로, 기능 동작만이 아니라 **각 행동 뒤 `recommendation_events` 적재를 반드시 확인**한다.

## 0. 사전 확인

- [ ] `flyway_schema_history`에 V40(`recommendation_event_food_mapping`)·V41(SWAPPED + `alternative_index`) 성공 기록
- [ ] 테스트 계정에 활성 목표 또는 프로필 영양 목표 존재 (없으면 추천이 정상적으로 422 실패해야 함 — 5단계에서 별도 검증)

## 1. 추천 생성 → GENERATED 적재

- [ ] iOS 식단 탭 → 오늘 추천 식단 진입 → 추천 생성 성공 (`POST /api/v1/diet/recommendations/daily`)
- [ ] 응답에 primary + 대안(기본 버퍼 4개), 끼니별 식품·제공량(`servingLabel` "N개" 표기 포함), 목표 대비 오차 표시
- [ ] 추천 결과에 등록된 알러지·제외 조건 위반 식품 없음 (**배포 게이트: 위반 0**)

```sql
-- snapshot 1행 + GENERATED가 food별로 fan-out 됐는지 (food_catalog_id NOT NULL)
SELECT event_type, COUNT(*) AS rows, COUNT(food_catalog_id) AS with_food
FROM recommendation_events
WHERE snapshot_id = :snapshotId
GROUP BY event_type;
-- 기대: GENERATED rows = 추천 식품 수, with_food = rows
```

## 2. 다시 추천 → REFRESHED 적재

- [ ] "다시 추천" 실행, 사유 선택 (`POST /api/v1/diet/recommendations/{snapshotId}/feedback`)
- [ ] 음식 기인 사유(최근에 먹음/조리 어려움/영양 불호) 1건 + 그 외 사유 1건 각각 전송
- [ ] 새 추천이 즉시 표시됨 (프리페치 대안 소진 시에도 로딩 정상)

```sql
-- REFRESHED가 사유와 함께 food별 fan-out 됐는지 (전 사유 저장 — 신호 산입은 집계 필터)
SELECT feedback_reason, COUNT(*) FROM recommendation_events
WHERE snapshot_id = :snapshotId AND event_type = 'REFRESHED'
GROUP BY feedback_reason;
```

## 3. 대안 swap → SWAPPED 적재 (V41)

- [ ] 대안 식단으로 swap (iOS `applyAlternative` → `POST /api/v1/diet/recommendations/{snapshotId}/swap`)
- [ ] swap 후 UI 정상 (계측은 best-effort — 실패해도 UX 무영향인지 확인)

```sql
-- SWAPPED 1행, alternative_index 기록, food는 null이 정상(#85 본 이슈)
SELECT event_type, alternative_index, food_catalog_id
FROM recommendation_events
WHERE snapshot_id = :snapshotId AND event_type = 'SWAPPED';
```

## 4. 기록 전환 → RECORDED 적재

- [ ] 추천 끼니를 기록으로 저장 (`POST /api/v1/diet/logs`, `recommendationSnapshotId` 포함)
- [ ] 저장된 기록이 일반 식단 기록처럼 조회·수정·삭제되고 오늘 영양 합계에 반영됨
- [ ] 수동 기록(추천 무관)도 1건 저장 — `recommendationSnapshotId` 없이 정상 동작, RECORDED 미적재 확인

```sql
-- RECORDED는 기록∩스냅샷 교집합 식품만 귀속
SELECT COUNT(*) FROM recommendation_events
WHERE snapshot_id = :snapshotId AND event_type = 'RECORDED';
-- 기대: 기록한 끼니의 스냅샷 소속 식품 수와 일치
```

## 5. 실패 경로 → Counter 적재

- [ ] 영양 목표 없는 계정으로 추천 요청 → `422 BUSINESS_RULE_VIOLATION` + 이해 가능한 실패 사유
- [ ] 메트릭 `healthcare.diet.recommendation.failure{reason}` 증가 확인 (실패는 이벤트 미적재라 이 Counter가 실패율 KPI의 유일한 소스)

## 6. 종합 판정

- [ ] 배포 게이트 0 유지: 알러지 위반 / hard 제약 위반 / 허용 안 된 제공량 / 재현성 위반
- [ ] 위 SQL 전부 기대값 일치 → **관찰 시계 시작 선언** (시작일 기록: ____)
- [ ] 하나라도 불일치 → 관찰 시작 보류, 원인 이슈 등록 후 재스모크
