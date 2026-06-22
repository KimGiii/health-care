# 식품 카탈로그 출처 우선순위 dedup 적재 설계

> 근거 데이터: [FOOD_API_CENSUS_DEDUP_PROFILE.md](../references/FOOD_API_CENSUS_DEDUP_PROFILE.md)
> (3 API 전수 902,498행 → 고유 256,925~321,118, **그대로 적재 시 ~64~72% 잉여**)

> **구현 상태(2026-06-22):** 메커니즘 구현 완료(이슈 #68). 단, 본 설계 §3의 `is_canonical`·
> `superseded_by_id`는 **신설하지 않고** V35 `canonical_group_id`를 재사용한다(대표 = `canonical_group_id IS NULL`,
> 패자 = 대표 id를 가리킴). 신설은 `dedup_group`·`dedup_name_key`·`dedup_state`(V39)뿐이다.
> 근거: [backend ADR-0005](../../backend/docs/adr/0005-source-priority-dedup-canonical-reuse.md).
> 따라서 §5의 후보 풀·검색 게이트는 기존 `canonical_group_id IS NULL` 필터를 그대로 쓴다(추가 술어 불필요).
> **백필·전량 적재는 미실행**(검증 후 별도 수행).

## 1. 문제 (현 모델의 격차)

현 적재 dedup은 `(source, food_code)` partial-unique 한 겹뿐이다
([V23 마이그레이션](../../backend/src/main/resources/db/migration/V23__food_catalog_source_recommendation_fields.sql) `uq_food_catalog_source_food_code`,
[FoodCatalogIngestService](../../backend/src/main/java/com/healthcare/domain/diet/external/importer/FoodCatalogIngestService.java) `findBySourceAndFoodCode`).

| 중복 유형 | 현재 처리 | 격차 |
|---|---|---|
| 가공식품 자체 2× (동일 `(source,food_code)`) | ✅ upsert로 합쳐짐 | 없음 |
| **출처 간 동일 food_code 294,391** | ❌ 출처마다 별도 행 3벌 적재 | **제거 대상** |
| 음식 ⊂ 영양DB (음식 코드 100% 겹침) | ❌ 별도 적재 | **자동 흡수 필요** |
| 출처 간 코드 충돌 3,961 (같은 코드·다른 품목) | ❌ 구분 안 됨 | **분리 + 검토** |

MFDS 3 API 는 **공통 food_code 공간**을 쓴다(`P…`=가공, `D…`=음식·영양DB 공유). 따라서 출처를 가로지르는
정규 식별이 필요하다.

## 2. 결정 사항

- **출처 우선순위**(canonical 채택): `SEED > BRAND_OFFICIAL > MFDS_STANDARD_PROCESSED > MFDS_FOOD_NUTRIENT_DB > MFDS_STANDARD_DISH`.
  `USER_CUSTOM`(isCustom)은 dedup 비대상(사용자별 데이터, 기존 경로 유지). SEED 는 수기 큐레이션이라 공공데이터에 밀리지 않도록 최상위.
- **패자 처리**: `is_canonical=false` 로 행은 보존하되 검색·추천에서 제외(provenance 유지, 정책 변경 시 재평가 가능).

## 3. 정규 식별 모델

행 그룹핑·canonical 유일성을 위한 신규 컬럼(모두 `food_catalog`):

| 컬럼 | 의미 |
|---|---|
| `dedup_group VARCHAR(60)` | = `food_code`(공통 코드 공간). 비공개·코드없음 행은 NULL(dedup 비대상). |
| `dedup_name_key VARCHAR(160)` | `FoodCatalogIdentity.duplicateNameKey(nameKo)` — 클러스터 분리용. |
| `is_canonical BOOLEAN` | 그룹 대표 여부(검색/추천 노출). 기본 true. |
| `dedup_state VARCHAR(20)` | `CANONICAL` / `SUPERSEDED`(내용 일치, 우선순위에 밀림) / `COLLISION`(동일 코드·다른 이름). |
| `superseded_by_id BIGINT` | 자기참조 FK — 이 행을 흡수한 canonical 행(provenance). |

**canonical 유일성 = `(dedup_group, dedup_name_key)`** 단위. 즉:
- 같은 코드 + 같은 이름키 → 한 식품으로 보고 우선순위 1개만 canonical.
- 같은 코드 + 다른 이름키(충돌) → 서로 다른 식품 → 각 클러스터가 각자 canonical(둘 다 노출) + `COLLISION` 플래그로 검토 큐.
- 영양값만 다르고 이름 같음(1,360건) → 같은 클러스터로 병합, 우선순위 출처의 영양값 채택.

```sql
-- Vxx__food_catalog_source_priority_dedup.sql
ALTER TABLE food_catalog
  ADD COLUMN dedup_group      VARCHAR(60),
  ADD COLUMN dedup_name_key   VARCHAR(160),
  ADD COLUMN is_canonical     BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN dedup_state      VARCHAR(20) NOT NULL DEFAULT 'CANONICAL',
  ADD COLUMN superseded_by_id BIGINT REFERENCES food_catalog(id);

CREATE INDEX ix_food_catalog_dedup_group
  ON food_catalog (dedup_group) WHERE deleted_at IS NULL AND dedup_group IS NOT NULL;

-- (코드, 이름클러스터)당 canonical 1개 강제
CREATE UNIQUE INDEX uq_food_catalog_canonical
  ON food_catalog (dedup_group, dedup_name_key)
  WHERE is_canonical AND deleted_at IS NULL AND dedup_group IS NOT NULL;
```

기존 `uq_food_catalog_source_food_code` 는 유지(출처 내 upsert 불변).

## 4. 적재 알고리즘 (우선순위 인지, 순서 무관)

`FoodCatalogIngestService.ingest()` 의 행별 처리를 `CanonicalDedupResolver` 협력자로 분리.
수용 후보 `imported`(출처 S, 코드 F, 이름키 NK)마다:

```
1. 출처 내 upsert (현행 유지)
   existingSame = findBySourceAndFoodCode(S, F)
   if existingSame: updateSourceFactsFromImportedCatalog(...)  // 가공 2× 등은 여기서 흡수
   else: persist(imported)
   row = existingSame or imported
   row.dedupGroup = F; row.dedupNameKey = NK

2. 클러스터 canonical 결정  (F 가 NULL 이면 skip — 비공개/코드없음)
   canonical = findCanonical(F, NK)            // is_canonical=true 인 (F,NK)
   if canonical == null:
       row.markCanonical()
   elif canonical.id == row.id:
       (그대로 canonical 유지)
   else:
       if priority(S) > priority(canonical.source):
           canonical.supersedeBy(row)          // 구 canonical 강등
           row.markCanonical()
       else:
           row.supersedeBy(canonical)          // 신규가 패자

3. 충돌 감지
   others = findCanonicalsByGroup(F) 중 dedupNameKey != NK
   if others not empty:
       row.markCollision(); others.forEach(markCollision)   // 검토 큐 적재
```

- **순서 무관성**: 항상 우선순위를 비교해 승격/강등하므로, 출처 적재 순서와 무관하게 동일 결과로 수렴.
- 음식(최하위)은 영양DB canonical 존재 시 자동 `SUPERSEDED` → 별도 skip 코드 불필요.

`FoodCatalogSource.dedupPriority()` 로 정책을 코드에 고정(버전관리·테스트 가능):
`SEED 500 / BRAND_OFFICIAL 400 / PROCESSED 300 / NUTRIENT_DB 200 / DISH 100 / USER_CUSTOM = 비대상`.

## 5. 노출 게이트 (검색·추천에서 패자 숨김)

| 지점 | 변경 |
|---|---|
| [FoodCatalogRepository.searchAll](../../backend/src/main/java/com/healthcare/domain/diet/repository/FoodCatalogRepository.java) | WHERE 에 `AND (f.isCustom = TRUE OR f.isCanonical = TRUE)` 추가 |
| [DietRecommendationCandidatePool:127](../../backend/src/main/java/com/healthcare/domain/diet/recommendation/candidate/DietRecommendationCandidatePool.java) | Specification 에 `isCanonical = true` 술어 추가 |
| 검토 큐 API | 기존 `/api/v1/admin/diet/catalog/dedup/report` 확장: `dedup_state=COLLISION` + dup_key 교차후보(221,221) 노출 |

## 6. 영향 코드

- `FoodCatalogSource` — `dedupPriority()` 추가.
- `FoodCatalog` — 신규 필드 + `markCanonical()` / `supersedeBy(canonical)` / `markCollision()` / getter.
- `CanonicalDedupResolver`(신규) — §4 로직. `FoodCatalogIngestService` 가 위임.
- `FoodCatalogRepository` — `findCanonical(group,nameKey)`, `findCanonicalsByGroup(group)`.
- 검색/추천 쿼리 2곳 — `is_canonical` 게이트.
- dedup 리포트 서비스 — 충돌·검토 큐 확장.

## 7. 롤아웃 / 백필

1. 마이그레이션 적용(기본값 CANONICAL/true → 기존 행 무영향).
2. **백필 패스**: 비커스텀·코드보유 행 전체에 `dedup_group/name_key` 채우고 §4 의 canonical 선정 1회 실행.
   - 옵션 A(권장): 신규 우선순위 적재로 전체 재적재(체크포인트 배치) → 자연히 canonical 수렴.
   - 옵션 B: 기존 DB에 대해 SQL/배치 dedup 패스만 수행.
3. 검색·추천 게이트 활성화 → 중복이 결과에서 사라짐.

**예상 결과**: canonical 행 ≈ 290k~321k(음식 전량 + 가공·영양DB 교차분 흡수), 충돌 3,961 검토 큐, 나머지 ~58만 행은 `is_canonical=false` 보존.

## 8. 검증 (TDD)

- `CanonicalDedupResolver` 단위: 우선순위 승격/강등, **순서 무관성**(음식→영양DB, 영양DB→음식 동일 결과), 충돌 분리, 출처 내 upsert 불변.
- 통합: 3 출처 표본 적재 → canonical 수가 census 기대치와 일치, 음식 코드가 영양DB로 흡수되는지.
- 회귀: 검색/추천이 canonical만 반환(패자 미노출).

## 9. 미결 / 후속

- 우선순위에 SEED 최상위 배치 확정 여부(현 가정: 수기 큐레이션 보호).
- dup_key 교차 후보(221,221) 자동화 범위 — 1차는 검토 큐만, 자동 병합은 보류(동명이품 위험).
- 충돌 3,961 의 해소 정책(코드 정정 vs 양쪽 유지) — 운영 검토 후 결정.
