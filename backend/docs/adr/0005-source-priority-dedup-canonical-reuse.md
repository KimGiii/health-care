# 0005. 출처 우선순위 dedup은 canonical_group_id를 재사용한다

## Status

Accepted

## Context

3종 공공 식품 API(가공식품 표준데이터·음식 표준데이터·식품영양성분 DB)는 공통 food_code 공간을 쓴다.
전수 census 결과 그대로 적재 시 ~64~72% 중복(수용행 902,498 → 고유 256,925~321,118)이라,
출처를 가로지르는 정규(canonical) 식별이 필요하다(설계: `docs/exec-plans/DIET_FOOD_CATALOG_SOURCE_PRIORITY_DEDUP.md`, 이슈 #68).

설계 초안(§3)은 `is_canonical`, `superseded_by_id`, `dedup_state`, `dedup_group`, `dedup_name_key`를
신설하자고 제안했다. 그러나 V35에서 이미 `canonical_group_id`(NULL = 그룹 대표 → 추천/검색 노출,
NOT NULL = 대표를 가리키는 중복)가 도입되어 `FoodCatalogSpecs.isCanonicalCandidate()`와
추천 후보 풀에 연결돼 있었다. 즉 "대표 여부"와 "supersede 포인터"는 이미 존재했고,
설계의 `is_canonical`/`superseded_by_id`와 의미가 정확히 겹쳤다.

## Decision

`canonical_group_id`(V35)를 supersede 포인터로 **재사용**한다.

- 대표(canonical) = `canonical_group_id IS NULL` (= 설계의 `is_canonical`). 기존 추천/검색 게이트를 그대로 쓴다.
- 패자(superseded) = `canonical_group_id`가 대표 행 id를 가리킨다 (= 설계의 `superseded_by_id`).
- 신설 컬럼은 클러스터 키와 상태뿐이다(V39): `dedup_group`(= food_code), `dedup_name_key`
  (`FoodCatalogIdentity.duplicateNameKey`), `dedup_state`(CANONICAL/SUPERSEDED/COLLISION).
- canonical 유일성 = `(dedup_group, dedup_name_key)` 부분 유니크 인덱스 `WHERE canonical_group_id IS NULL`.
- 출처 우선순위는 `FoodCatalogSource.dedupPriority()`에 고정한다
  (SEED 500 > BRAND_OFFICIAL 400 > PROCESSED 300 > NUTRIENT_DB 200 > DISH 100, USER_CUSTOM 비대상).
- 행별 정규화는 `CanonicalDedupResolver`가 담당하고 `FoodCatalogIngestService`가 위임한다.
  항상 우선순위를 비교해 승격/강등하므로 적재 순서와 무관하게 동일 결과로 수렴한다.

## Consequences

- 추천 후보 풀·검색 게이트는 `canonical_group_id IS NULL`을 그대로 재사용해 변경·재배선이 없다(DRY, 저위험).
- 단일 진실 출처: "대표 여부"가 `canonical_group_id` 한 곳에만 있다(`is_canonical` 불리언 중복 회피).
- dedup 패자(superseded)는 추천 대상이 아니므로 적재 시 ServingOption을 생성하지 않고 정리한다 →
  옵션 행 폭증을 직접 차단한다. 단, 다른 행 적재로 강등된 구 대표의 옵션 정리는 그 행 재적재 시 수렴한다(후속 백필 보완 대상).
- 충돌(같은 코드·다른 이름) 3,961건은 양쪽을 `COLLISION`으로 표시해 검토 큐로 남기고, 자동 병합은 보류한다(동명이품 위험).
- 전량 적재·백필 실행은 본 결정 범위가 아니다. dedup 메커니즘 검증 후 별도로 수행한다(이슈 #68).
