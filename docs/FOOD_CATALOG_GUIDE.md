# 식품 카탈로그 기술 가이드

**작성일**: 2026-05-04

**개정일**: 2026-06-10

**상태**: MVP 구현 완료 / Phase 1 스키마 + Phase 2 배치 파이프라인·중복 리포터·관리자 API + Phase 3 브랜드 CSV + Phase 4 추천 큐레이션 응답 구현 / staging 전량 적재 판단 근거 정리

**작업 브랜치**: `feat/allegen-recommendation`

알러젠 식단 추천과 식품 카탈로그 강화 작업은 `feat/allegen-recommendation` 브랜치에서만 진행합니다. `dev`에는 직접 커밋하지 않고, 검증된 변경을 PR/머지로 반영합니다.

## 개요

HealthCare 앱의 식품 카탈로그는 공공 데이터(식품디비) + 사용자 커스텀 식품의 조합으로 운영됩니다. 이 문서는 2026-05-04에 구현된 **사용 횟수 추적** 및 **사용자 직접 등록** 기능을 설명합니다.

2026-06-09 기준으로 식품 카탈로그 강화 방향이 확정되었습니다. 2026-06-10에는 V23 스키마 보강과 추천 후보 필터(Phase 1)에 이어, 공공데이터 배치 적재 파이프라인, 동일 추정 중복 후보 리포터, 관리자 실행 API(Phase 2)가 완료되었습니다. 2026-06-17에는 버거킹·맥도날드·롯데리아 공식 메뉴 CSV 376행을 local DB에 적재하고, 브랜드별 4개씩 총 12개를 `RECOMMENDABLE_WITH_CAUTION` 출시 후보로 승격했습니다.

앞으로 `food_catalog`는 **식단 기록 검색용 + 식단 기록 계산용 + 식단 추천 후보 풀**의 공통 기반으로 운영합니다. 다만 모든 카탈로그 항목이 추천 후보가 되는 것은 아니며, 추천 가능 여부는 메뉴/식품 단위의 추천 적합성 상태로 분리합니다.

## 카탈로그 운영 모델

### 공통 카탈로그 원칙

사용자는 식단 기록 시 내부 `food_catalog`를 검색하고, 선택한 식품의 영양값으로 식단 기록 합계를 계산합니다. 추천 기능도 같은 `food_catalog`를 기반으로 하지만, 추천 런타임에서는 전체 카탈로그가 아니라 추천 적합성 상태를 통과한 항목만 후보로 사용합니다.

### 데이터 소스

| 소스 | 역할 |
|---|---|
| 전국통합식품영양성분정보 가공식품 표준데이터 | 현재 사용 중인 가공식품 공공데이터. 내부 카탈로그에 사전 적재 |
| 전국통합식품영양성분정보 음식 표준데이터 | 현재 사용 중인 음식/외식 공공데이터. 내부 카탈로그에 사전 적재 |
| 식약처 식품영양성분DB정보 (`FoodNtrCpntDbInfo02`) | 기존 표준데이터와 비교 후 1회 제공량, 식품중량, 제조사/업체명 등 보강 |
| 브랜드 공식 메뉴 CSV | 버거킹, BBQ, 서브웨이, 샐러디, 프레퍼스 등 상위 브랜드 일부를 관리자 검수 후 적재 |
| 사용자 커스텀 식품 | 검색/기록 가능. 검증 전 추천 후보에서는 제외하거나 보수적으로 취급 |

원재료성식품 표준데이터는 v1 필수 범위에서 제외하고, 식재료 단위 추천이나 장보기 기능에서 후속 검토합니다.

### 검색/기록과 추천의 분리

| 상태 | 의미 | 검색/기록 | 추천 |
|---|---|---:|---:|
| `SEARCH_ONLY` | 검색/기록 가능, 추천 제외 | O | X |
| `RECOMMENDABLE` | 일반 추천 후보 | O | O |
| `RECOMMENDABLE_WITH_CAUTION` | 나트륨·당류·포화지방 등 주의 표시와 함께 추천 가능 | O | O |
| `DISABLED` | 데이터 불완전, 검수 실패, 만료 등으로 비활성 | X | X |

예를 들어 와퍼세트나 치킨 메뉴는 사용자가 직접 검색해 기록할 수 있지만 기본 추천 후보에서는 제외될 수 있습니다. 반대로 서브웨이, 샐러디, 프레퍼스의 일부 메뉴는 제공량 기준 영양값이 정책 기준을 통과하면 추천 후보가 될 수 있습니다.

### 적재 방식

사용자 검색/추천 시점에 외부 API를 직접 호출하지 않습니다. 공공데이터와 브랜드 공식 메뉴는 배치 또는 관리자 작업으로 내부 DB에 적재하고, 앱 기능은 내부 `food_catalog`를 조회합니다.

### 성능 원칙

- 검색은 내부 DB 기준으로 수행합니다.
- 검색 품질이 부족하면 `normalized_name`과 PostgreSQL `pg_trgm` 인덱스를 검토합니다.
- 추천은 `recommendation_status`, 카테고리, 제한 조건, 기본 영양 조건을 DB WHERE 절에서 먼저 적용합니다.
- 추천 엔진에는 제한된 후보만 전달합니다.

현재 추천 후보 조회는 `RECOMMENDABLE`, `RECOMMENDABLE_WITH_CAUTION` 상태만 포함합니다. `SEARCH_ONLY`, `DISABLED` 항목은 검색/기록 정책과 별개로 추천 후보에서는 제외됩니다.

나트륨·당류·포화지방 기준값으로 추천 상태를 판정하는 작업은 런타임 추천 로직과 분리된 데이터 운영 작업입니다. v1에서는 CSV/관리자 검수로 `recommendation_status`를 명시하되, 브랜드 공식 메뉴 CSV 입력에서 아래 기준을 넘는 항목을 일반 `RECOMMENDABLE`로 넣으면 row를 거절합니다.

추천 주의 기준값:

| 영양소 | 1회 제공량 기준 | `RECOMMENDABLE_WITH_CAUTION` 사유 |
|---|---:|---|
| 나트륨 | 600mg 이상 | `나트륨 주의` |
| 당류 | 15g 이상 | `당류 주의` |
| 포화지방 | 4.5g 이상 | `포화지방 주의` |

부여 기준:

1. 위 기준 중 하나라도 넘고 추천 후보로 쓸 수 있는 메뉴는 `RECOMMENDABLE_WITH_CAUTION`으로만 승격합니다.
2. 여러 기준을 넘으면 `나트륨/당류/포화지방 주의`처럼 고정 순서로 사유를 합칩니다.
3. 기준을 넘는 항목을 `RECOMMENDABLE`로 입력하면 CSV row를 거절합니다.
4. 기준을 넘더라도 메뉴 자체가 추천 목적과 맞지 않거나 세트/대용량/영양 결측이 크면 `SEARCH_ONLY`를 유지합니다.
5. 이 기준은 개인별 의학적 권고가 아니라 카탈로그 추천 후보 운영 기준입니다.

참고 근거:

- [WHO Sodium reduction](https://www.who.int/news-room/fact-sheets/detail/sodium-reduction): 성인 나트륨 섭취 권고 상한 2000mg/day 미만
- [WHO Sugars intake guideline](https://www.who.int/news/item/04-03-2015-who-calls-on-countries-to-reduce-sugars-intake-among-adults-and-children): free sugars를 총 에너지의 10% 미만으로 제한, 5% 미만이면 추가 이점
- [WHO fats/carbohydrates guideline update](https://www.who.int/news/item/17-07-2023-who-updates-guidelines-on-fats-and-carbohydrates): 포화지방은 총 에너지의 10% 이하 권고
- 국내 브랜드 공식 영양표의 포화지방 일일 기준치 표기는 15g/day 기준으로 해석됩니다.

추천 다양성은 큐레이션 상태가 아니라 추천 엔진의 선택 전략에서 다룹니다. 같은 사용자/날짜/입력에서는 재현 가능한 결과를 주되, 날짜가 바뀌면 후보 우선순위가 회전하도록 deterministic rotation을 적용합니다. 최근 추천/최근 기록 기반 중복 억제는 v2 품질 개선으로 분리합니다.

## 주요 기능

### 0. 카탈로그 강화 메타데이터 (V23)

#### 데이터베이스 변경

V23 마이그레이션에서 `food_catalog`에 다음 운영 메타데이터를 추가했습니다.

```sql
ALTER TABLE food_catalog
    ADD COLUMN food_code             VARCHAR(60),
    ADD COLUMN source                VARCHAR(40),
    ADD COLUMN source_detail         VARCHAR(120),
    ADD COLUMN brand_name            VARCHAR(150),
    ADD COLUMN maker                 VARCHAR(150),
    ADD COLUMN serving_size_g        DOUBLE PRECISION,
    ADD COLUMN serving_reference     VARCHAR(80),
    ADD COLUMN recommendation_status VARCHAR(40),
    ADD COLUMN recommendation_reason VARCHAR(255),
    ADD COLUMN data_version          VARCHAR(80),
    ADD COLUMN last_verified_at      TIMESTAMPTZ;
```

백필/큐레이션 원칙:

- 기존 시드 식품: `source = SEED`, 추천 상태는 명시 큐레이션 목록에 따라 결정
- 기존 사용자 커스텀 식품: `source = USER_CUSTOM`, `recommendation_status = SEARCH_ONLY`
- seed 전체를 자동 `RECOMMENDABLE`로 보지 않습니다. 컵라면, 주류, 설탕, 마요네즈, 튀김류, 고지방 가공육처럼 검색/기록에는 유용하지만 추천 후보로 부적합하거나 주의 표시가 필요한 항목이 섞여 있기 때문입니다.
- seed allowlist는 전체 40~60개 이상을 목표로 하고, 단백질 10개 이상, 곡류/주식 8개 이상, 채소 10개 이상, 과일 6개 이상, 유제품/간식 대체 4개 이상을 최소 운영 기준으로 둡니다. 기준을 만족하지 못하면 추천 엔진을 임시로 넓히기보다 seed/브랜드 후보를 먼저 보강합니다.

추가 인덱스:

```sql
CREATE UNIQUE INDEX uq_food_catalog_source_food_code
  ON food_catalog (source, food_code)
  WHERE food_code IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_food_catalog_recommendation_status
  ON food_catalog (recommendation_status)
  WHERE deleted_at IS NULL;
```

##### 제공량 필드 해석 (중요)

`serving_size_g`를 "1회 제공량"으로 쓰면 안 됩니다. dev DB(62만 건) 프로파일링 기준:

- `MFDS_FOOD_NUTRIENT_DB`: 99.99%가 `serving_size_g = 100` 플레이스홀더(Z10500/foodWeight 유래).
- `MFDS_STANDARD_PROCESSED`: **포장 총중량** — 16%가 1000g↑, 10kg 벌크 포함. 기록 기본량으로 쓰면 안 됨.

권위 있는 1회 제공량은 **`food_serving_options`(전부 `serving_type=OFFICIAL_SERVING`)**이며, `ServingOptionDeriver`가 `serving_reference`(제조사 표기 1회 제공량, 텍스트)에서 파생합니다. 대표 옵션은 `sort_order = 0`. 검색 응답은 `FoodCatalogResponse.servingOptions`로 이미 노출됩니다(`FoodCatalogService.searchFoods`가 옵션 동반 로딩).

**소비자(iOS 등) 규칙**: 기본 제공량 = ① `servingOptions` 대표(OFFICIAL_SERVING, sort 0) `equivalentG` → ② 브랜드 공식 메뉴면 `servingSizeG` → ③ 100g. `serving_size_g`는 직접 쓰지 않습니다.

> 영양소(`calories_per_100g` 등)는 **진짜 100g당**이 맞습니다. MFDS 통합DB가 100g/100mL로 표준화하며, `NUTRI_AMOUNT_SERVING`/`serving_reference`는 영양소 기준량이 아니라 제공량 설명입니다(증거: 식용유 기준량 "5g(ml)"인데 `calories_per_100g = 900`). 따라서 영양값 정규화/이중계산 보정은 불필요합니다.

#### 구현 위치

```
backend/src/main/java/com/healthcare/domain/diet/
├── entity/FoodCatalog.java              # V23 필드 반영
├── entity/FoodCatalogSource.java        # 출처 enum
├── entity/RecommendationStatus.java     # 추천 상태 enum
├── dto/FoodCatalogResponse.java         # 응답 메타데이터 확장
├── repository/FoodCatalogRepository.java # source + foodCode 조회, usage_count 갱신
├── repository/FoodCatalogSpecs.java     # 추천 후보 상태 Specification
├── service/FoodCatalogService.java      # 커스텀 식품 기본값 USER_CUSTOM / SEARCH_ONLY
├── recommendation/candidate/          # 추천 후보 풀과 Engine 입력 후보 값
├── recommendation/usecase/DailyDietRecommendationUseCases.java # 후보 풀 로드 후 Engine 호출
├── external/service/FoodImportService.java # 외부 import 기본값 USER_CUSTOM / SEARCH_ONLY
└── external/importer/                  # 공공데이터 배치 적재
    ├── StandardProcessedFoodImporter.java
    ├── StandardDishFoodImporter.java
    ├── MfdsFoodNutrientDbImporter.java
    ├── StandardProcessedFoodPageFetcher.java
    ├── StandardDishFoodPageFetcher.java
    ├── MfdsFoodNutrientDbPageFetcher.java
    ├── FoodCatalogImportBatchRunner.java
    ├── JpaFoodCatalogImportCheckpointStore.java
    └── FoodCatalogPublicDataImportService.java
├── external/dedup/                     # 중복 후보 리포터
│   ├── FoodCatalogDuplicateCandidateReporter.java
│   ├── FoodCatalogDuplicateCandidateReport.java
│   ├── FoodCatalogDuplicateGroup.java
│   ├── FoodCatalogDuplicateReportService.java
│   └── (Response DTOs)
└── controller/FoodCatalogAdminController.java  # 관리자 API
```

#### API 응답 추가 필드

`FoodCatalogResponse`는 다음 필드를 추가로 반환합니다.

- `foodCode`
- `source`
- `sourceDetail`
- `brandName`
- `maker`
- `servingSizeG`
- `servingReference`
- `recommendationStatus`
- `recommendationReason`
- `dataVersion`
- `lastVerifiedAt`

### 0.1 공공데이터 row importer

Phase 2의 첫 구현으로 공공데이터 row를 내부 `food_catalog`로 적재하는 importer를 추가했습니다.

| importer | 원본 | source | source_detail |
|---|---|---|---|
| `StandardProcessedFoodImporter` | 전국통합식품영양성분정보 가공식품 표준데이터 | `MFDS_STANDARD_PROCESSED` | `15100066` |
| `StandardDishFoodImporter` | 전국통합식품영양성분정보 음식 표준데이터 | `MFDS_STANDARD_DISH` | `15100070` |
| `MfdsFoodNutrientDbImporter` | 식품영양성분DB정보 `FoodNtrCpntDbInfo02` | `MFDS_FOOD_NUTRIENT_DB` | `FoodNtrCpntDbInfo02` |

공통 적재 규칙:

- `source + food_code` 기준으로 기존 항목을 찾고, 없으면 생성합니다.
- 같은 `source + food_code`가 다시 들어오면 기존 항목의 원본 메타데이터와 영양값을 갱신합니다.
- 공공데이터 재적재는 추천 검수 상태(`recommendation_status`, `recommendation_reason`)를 덮어쓰지 않습니다.
- `food_code`, 식품명, 열량이 없거나 파싱할 수 없으면 skip 처리합니다.
- 공공데이터로 들어온 항목은 기본 `SEARCH_ONLY`로 저장합니다. v1에서는 공공데이터 항목을 추천 후보로 대량 자동 승격하지 않고, 추천 후보는 기존 검수 seed와 `BRAND_OFFICIAL` CSV 검수 항목 중심으로 제한합니다.
- 공공데이터 항목 승격이 필요해지면 개별 수정 API보다 `source + food_code` 기준 큐레이션 오버레이 CSV를 별도 운영 작업으로 검토합니다.
- `last_verified_at`은 원본 기준일을 KST 자정 기준으로 저장합니다.

### 0.2 공공데이터 page fetcher와 배치 runner

Phase 2의 두 번째 구현으로 공공데이터 API 페이지 순회와 재시작 체크포인트를 추가했습니다.

| 구성요소 | 역할 |
|---|---|
| `StandardProcessedFoodPageFetcher` | 15100066 가공식품 표준데이터 API 응답을 `StandardFoodImportRow`로 변환 |
| `StandardDishFoodPageFetcher` | 15100070 음식 표준데이터 API 응답을 `StandardFoodImportRow`로 변환 |
| `MfdsFoodNutrientDbPageFetcher` | `FoodNtrCpntDbInfo02` API 응답을 `MfdsFoodNutrientDbImportRow`로 변환 |
| `FoodCatalogImportBatchRunner` | 체크포인트 다음 페이지부터 fetch/import/checkpoint 저장을 반복 |
| `JpaFoodCatalogImportCheckpointStore` | source별 마지막 완료 페이지를 DB에 저장 |
| `FixedDelayFoodCatalogImportPageThrottle` | 페이지 사이 rate limit 대기. 기본 0ms |
| `FoodCatalogPublicDataImportService` | 공공데이터 3종 적재 진입점 |

V24 마이그레이션에서 체크포인트 테이블을 추가했습니다.

```sql
CREATE TABLE food_catalog_import_checkpoints (
    source              VARCHAR(40) PRIMARY KEY,
    last_completed_page INTEGER NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

배치 runner는 페이지 fetch나 row import 중 예외가 발생하면 실패한 페이지를 완료 체크포인트로 저장하지 않습니다. 따라서 다음 실행은 마지막 완료 페이지 다음부터 재개합니다.

관련 설정:

```yaml
app:
  admin:
    operation-token: ${ADMIN_OPERATION_TOKEN:}
  food-api:
    public-api-key: ${PUBLIC_FOOD_API_KEY:}
    processed-food-api-url: https://api.data.go.kr/openapi/tn_pubr_public_nutri_process_info_api
    general-food-api-url: https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api
    food-nutrient-db-api-url: https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02
    import-page-delay-millis: 0
```

### 0.3 중복 후보 리포터와 관리자 API

#### 중복 후보 리포터

`FoodCatalogDuplicateCandidateReporter`는 정규화 이름 기준으로 동일 추정 중복 그룹을 찾습니다. DB 의존 없이 `List<FoodCatalog>`를 받아 in-memory로 동작합니다.

정규화 규칙:
- `nameKo`(없으면 `name`) 기준
- 소문자 변환 후 `[공백, -, _, /, (), （）]` 제거

자동 병합은 하지 않습니다. 그룹 정보(`normalizedKey`, 항목 목록), source priority 기준 대표 후보(`suggestedCanonicalId`), entry별 `sourcePriorityRank`만 반환하며, 병합 여부는 운영자가 직접 판단합니다.

source priority:

| 순위 | source | 판단 |
|---:|---|---|
| 1 | `BRAND_OFFICIAL` | 브랜드 공식 자료와 수동 검수일이 있어 같은 브랜드 메뉴의 대표 후보로 우선 |
| 2 | `MFDS_STANDARD_DISH` | 외식/일반 음식 커버리지와 음식명 맥락이 좋음 |
| 3 | `MFDS_STANDARD_PROCESSED` | 제조사/품목 식별에 강한 가공식품 기본 소스 |
| 4 | `MFDS_FOOD_NUTRIENT_DB` | 기존 2종의 보강/비교 소스 |
| 5 | `SEED` | 초기 추천 후보 유지용. 공공/공식 소스가 있으면 대표성 낮음 |
| 6 | `USER_CUSTOM` | 사용자 기록용. 비커스텀 dedup 리포트에는 기본 포함하지 않음 |

수동 병합 기준:

1. 같은 `normalizedKey`라도 자동 병합하지 않습니다.
2. 브랜드명이 다르면 같은 메뉴명이어도 병합하지 않습니다.
3. 같은 브랜드/메뉴 또는 동일 제품이라고 판단하려면 원문명, 제조사/브랜드, 제공량, 열량, 단백질, 나트륨, 당류, 포화지방을 함께 비교합니다.
4. 대표 후보는 source priority와 최신 검수일을 기준으로 제안할 뿐, 운영자가 원문 source URL 또는 공공데이터 식별자를 확인한 뒤 확정합니다.
5. 병합 실행은 v1에서 삭제가 아니라 `SEARCH_ONLY` 하향, 표시명 교정, alias 보강, 후속 migration/API 설계 중 하나로 처리합니다.

#### 관리자 API 엔드포인트

`FoodCatalogAdminController` — `/api/v1/admin/diet/catalog`

모든 관리자 카탈로그 작업은 일반 사용자 JWT 인증과 별도로 `X-Admin-Token` 헤더가 필요합니다. 서버의 `app.admin.operation-token`이 비어 있거나 요청 헤더가 일치하지 않으면 403으로 거부됩니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/import/processed-foods` | 가공식품 표준데이터(15100066) 배치 적재 |
| `POST` | `/import/dish-foods` | 음식 표준데이터(15100070) 배치 적재 |
| `POST` | `/import/nutrient-db` | 식품영양성분DB(`FoodNtrCpntDbInfo02`) 배치 적재 |
| `POST` | `/import/brand-csv` | 브랜드 공식 메뉴 CSV 수동 검수 적재 |
| `GET` | `/dedup/report` | 비커스텀 카탈로그 전체 대상 중복 후보 리포트(전체 메모리 적재) |
| `POST` | `/dedup/backfill` | 기존 행을 출처 우선순위 canonical로 1회 수렴(멱등·재실행 안전) + 패자 ServingOption 정리 |
| `GET` | `/dedup/collisions` | 같은 코드·다른 이름 충돌(COLLISION) 검토 큐. `afterCode`·`limit` 코드 커서 페이지네이션 |

import 엔드포인트는 `pageSize`(기본 100, 최대 500)와 `maxPages`(기본 500, 최대 500) 쿼리 파라미터를 받습니다. 상한을 벗어나면 실제 적재를 시작하지 않고 400으로 거부합니다. 응답은 `FoodCatalogBatchImportSummary`(source, startPage, lastCompletedPage, fetchedPageCount, created/updated/skipped 수, `attemptedCount`, `skippedRatio`, exhausted 여부)를 포함합니다.

#### 공공데이터 전량 적재 운영 순서

2026-06-18 기준 운영 전량 적재는 즉시 실행하지 않고, staging 전량 적재와 데이터 재사용 조건 확인을 먼저 통과해야 합니다. 결정 근거와 운영 기록 양식은 `docs/references/FOOD_CATALOG_DATA_REUSE_AND_STAGING_VERIFICATION_2026-06-18.md`를 기준으로 합니다.

공공데이터 전량 적재는 source별 체크포인트를 사용하는 반복 실행 작업입니다. 한 번의 관리자 API 호출이 전체 데이터 적재를 보장하지 않으므로, 각 source의 응답 summary에서 `exhausted=true`가 나올 때까지 같은 source를 반복 실행합니다. 이 절차는 staging/운영 DB 기준 runbook이며, local DB에서는 smoke, 제한 배치, 대표 장애 케이스 검증까지만 수행해도 충분합니다.

| 순서 | 작업 | 호출/확인 |
|---:|---|---|
| 0 | 사전 조건 확인 | DB 백업, Flyway V23/V24/V25 적용, `PUBLIC_FOOD_API_KEY`, `ADMIN_OPERATION_TOKEN`, `app.food-api.import-page-delay-millis` 설정 |
| 1 | 실제 API smoke | 각 source를 `pageSize=100&maxPages=1`로 1페이지 호출 |
| 2 | 제한 배치 | `processed-foods` → `dish-foods` → `nutrient-db` 순서로, smoke와 같은 `pageSize=100`을 유지해 `maxPages=2~5` 실행 |
| 3 | rate limit 확정 | timeout/429 여부를 보고 `import-page-delay-millis` 조정 |
| 4 | 가공식품 전량 적재 | smoke/제한 배치와 같은 `pageSize`로 `/import/processed-foods` 반복 실행, `exhausted=true`까지 |
| 5 | 음식 전량 적재 | 같은 `pageSize`로 `/import/dish-foods` 반복 실행, `exhausted=true`까지 |
| 6 | 식품영양성분DB 전량 적재 | 같은 `pageSize`로 `/import/nutrient-db` 반복 실행, `exhausted=true`까지 |
| 7 | 적재 검증 | source별 row count, 체크포인트, skipped 비율, 대표 검색어 조회 확인 |
| 8 | canonical dedup 수렴 | `/dedup/backfill` 실행(또는 적재가 행별 수렴) → dedup_state 분포가 acceptance target과 일치하는지 확인. 자동 병합 금지 |
| 9 | 충돌 검토 큐 | `/dedup/collisions` 로 COLLISION 코드 검토 목록 생성(코드 커서 페이지네이션) |
| 10 | 후속 큐레이션 | local 기준 브랜드 CSV 보강과 추천 후보 12개 승격 완료. staging/운영에서는 동일 CSV 재적재 후 source/status count와 caution 노출을 검증 |

#### canonical dedup 적재 검증 (#68)

출처 간 동일 식품을 정규(canonical) 1개로 수렴시키는 dedup은 별도 설계·검증을 거친다. 상세: [DIET_FOOD_CATALOG_SOURCE_PRIORITY_DEDUP](exec-plans/DIET_FOOD_CATALOG_SOURCE_PRIORITY_DEDUP.md).

- **acceptance target (전수 census 재생, read-only)**: 적재 615,509행 → canonical 323,899 / superseded 291,610(47.4%) / COLLISION **코드 2,781 (= `dedup_state` 행 ~5,562, 코드당 2대표)**. 산출: `python3 scripts/food-census/census.py project` → [FOOD_CATALOG_DEDUP_LOAD_PROJECTION](references/FOOD_CATALOG_DEDUP_LOAD_PROJECTION.md). 적재 후 `dedup_state`별 count(=행)가 이 값(+ 기존 SEED/BRAND 보정)과 일치해야 한다. ⚠️ COLLISION은 코드가 아니라 **행**으로 세므로 ~5,562와 비교. 로컬 전량 실측(2026-06-26): 2,872 코드 / 5,744 행(+3.3%, gov 성장), 그중 89%는 구두점만 다른 동일 제품.
- **G1 정합성 게이트(자동)**: `FoodCatalogDedupLoadIT`(실 PostgreSQL + Flyway V39 부분 유니크 인덱스)가 적재 경로 전체(수렴·검색/추천 패자 제외·ServingOption 대표 한정·백필 옵션 정리)를 검증. CI는 postgres 17 서비스에서 자동 실행.
- **옵션 폭증 차단**: ServingOption은 canonical 행에만 생성(대표당 4개 ≈ 130만). 강등된 구 대표 옵션은 `/dedup/backfill`의 정리 패스로 제거.
- ⚠️ **적재 부하 대상 = prod RDS db.t3.micro(앱 호스트 t3.medium과 별개)**. 전량 적재 전 일회성(ephemeral) RDS에서 용량 리허설 + 적재 창 동안 클래스 일시 상향 권장. **상시 dev RDS는 불필요**(정합성은 싼 DB, 용량은 일회성 RDS). dev/prod DB 토폴로지: dev는 박스 컨테이너 PG, prod만 RDS. **G2 리허설 절차**: [operations/FOOD_CATALOG_BULK_LOAD_RDS_REHEARSAL](operations/FOOD_CATALOG_BULK_LOAD_RDS_REHEARSAL.md).

주의: 체크포인트는 source별 마지막 완료 페이지 번호만 저장하고 `pageSize`는 저장하지 않습니다. 같은 source를 이어서 적재하는 동안 `pageSize`를 바꾸면 중간 row를 건너뛸 수 있습니다. smoke 후 다른 `pageSize`로 전환해야 한다면 해당 source의 smoke row와 체크포인트를 초기화한 뒤 다시 시작합니다.

권장 호출 예시:

```bash
curl -X POST "$BASE_URL/api/v1/admin/diet/catalog/import/processed-foods?pageSize=100&maxPages=500" \
  -H "X-Admin-Token: $ADMIN_OPERATION_TOKEN"
```

local에서 추가 적재를 이어 실행해야 한다면 이미 smoke/제한 배치에 사용한 `pageSize=100`을 유지합니다. 다만 local에 모든 공공데이터 row를 끝까지 적재하는 것은 필수 작업이 아닙니다.

전량 적재 완료 기준:

- `processed-foods`, `dish-foods`, `nutrient-db` 마지막 실행 응답이 모두 `exhausted=true`
- `food_catalog_import_checkpoints`에 source별 마지막 완료 페이지 기록 존재
- 마지막 실행 응답의 `attemptedCount`, `skippedCount`, `skippedRatio`를 source별 운영 기록으로 보존
- 신규 공공데이터 항목의 `recommendation_status` 기본값이 `SEARCH_ONLY`
- 대표 검색어가 내부 `food_catalog`에서 조회됨
- dedup 리포트를 실행했고 자동 병합 없이 검수 목록으로 분리함

dedup 리포트 응답 예시:

```json
{
  "totalGroups": 3,
  "totalCandidates": 7,
  "groups": [
    {
      "normalizedKey": "닭가슴살",
      "count": 3,
      "suggestedCanonicalId": 42,
      "entries": [
        { "id": 1, "name": "닭가슴살", "source": "SEED", "sourcePriorityRank": 5, "caloriesPer100g": 165.0, ... },
        { "id": 42, "name": "닭 가슴살", "source": "MFDS_FOOD_NUTRIENT_DB", "sourcePriorityRank": 4, ... },
        { "id": 87, "name": "닭가슴살(구운)", "source": "MFDS_STANDARD_PROCESSED", "sourcePriorityRank": 3, ... }
      ]
    }
  ]
}
```

#### 브랜드 공식 메뉴 CSV 계약

브랜드 공식 메뉴 CSV는 입력 영양값의 기준을 `nutrition_basis`로 명시합니다.

| `nutrition_basis` | 입력 영양값 기준 | `serving_size_g` | 처리 |
|---|---|---|---|
| `PER_SERVING` | 1회 제공량 전체 기준 | 필수 | 100g당 값으로 환산 저장 |
| `PER_100G` | 100g당 기준 | 선택 | 그대로 100g당 값으로 저장, 공식 전체 제공량이 있으면 기본 기록량으로 보존 |

CSV 헤더가 템플릿과 다르면 파일 전체를 거절합니다. 개별 row의 필수값, 숫자 형식, 제공량 기준이 잘못되면 해당 row만 저장하지 않고 `rejectedRows`에 row 번호, 필드, 사유를 반환합니다.

`recommendation_reason`은 `recommendation_status = RECOMMENDABLE_WITH_CAUTION`일 때만 저장되는 주의 사유입니다. 이 상태에서는 사유가 필수이며, 다른 상태의 사유 입력은 저장하지 않습니다.

v1에서 브랜드 공식 메뉴의 추천 상태 변경은 CSV 재업로드/재적재를 기준 경로로 둡니다. 개별 식품의 추천 상태를 직접 수정하는 관리자 API는 원본 CSV와 DB 상태가 갈라질 수 있으므로 즉시 제공하지 않습니다. 운영 중 CSV 재적재가 과하게 무겁다는 근거가 쌓이면 변경 이력과 원본 충돌 정책을 함께 설계한 뒤 후속으로 검토합니다.

알러젠 검토 컬럼은 포함 태그와 완결 프로필을 분리해서 해석합니다.

- `allergen_tags`: 공식 라벨/알러젠 표에서 확인한 포함 알러젠만 입력합니다. 한국어 라벨과 내부 enum 코드를 허용하며, 여러 값은 쉼표, `|`, `/` 중 하나로 구분합니다.
- `allergen_profile_verified`: 해당 식품의 표시대상 알러젠 집합을 공식 근거로 완결 검토했을 때만 `true`로 입력합니다.
- 포함 알러젠이 없는 제품도 공식 라벨에서 "해당 없음"을 확인했다면 `allergen_tags`를 비우고 `allergen_profile_verified=true`로 입력할 수 있습니다. 이 경우 `food_allergen_tags`는 비어 있고 `food_allergen_profiles`에 `LABEL_DERIVED` 완결 프로필이 저장됩니다.
- `allergen_profile_verified=true`에는 `last_verified_at`이 필수입니다. 검수일이 없으면 프로필 레코드의 유효성을 판단할 수 없으므로 row를 거절합니다.
- 브랜드 CSV가 알러젠 검토값을 포함하면 동일 `BRAND_OFFICIAL` 메뉴의 기존 `BRAND_OFFICIAL` 알러젠 태그를 CSV 내용으로 교체합니다.

#### 추천 큐레이션 CSV 계약

신규 식품 생성은 브랜드 CSV 또는 공공데이터 적재가 담당하고, 추천 큐레이션 CSV는 이미 존재하는 `source + food_code` row의 추천 자격과 검수 근거를 갱신합니다.

템플릿: [recommendation_curation_csv_template.csv](references/recommendation_curation_csv_template.csv)

필수 헤더:

- `source`
- `food_code`
- `recommendation_status`
- `recommendation_reason`
- `last_verified_at`
- `review_source_url`
- `allergen_tags`
- `allergen_profile_verified`

운영 경로:

1. `GET /api/v1/admin/diet/candidate-pool/curation-queue?limit=50`로 `SEARCH_ONLY` 중 승격 가치가 큰 canonical row를 조회합니다.
2. 신규 포장 SKU는 먼저 `POST /api/v1/admin/diet/catalog/import/brand-csv`로 `BRAND_OFFICIAL` row를 만들거나 갱신합니다.
3. `POST /api/v1/admin/diet/catalog/curation-csv`로 추천 상태와 검수 근거를 적용합니다.
4. `GET /api/v1/admin/diet/candidate-pool/summary`에서 `macroCompleteTotal`, `verifiedServingOptionTotal`, `allergenProfileVerifiedTotal`, `engineReadyTotal` 변화를 확인합니다.

추천 후보(`RECOMMENDABLE`, `RECOMMENDABLE_WITH_CAUTION`)로 승격하려면 다음 조건을 모두 만족해야 합니다.

- canonical 대표 row
- 열량, 단백질, 탄수화물, 지방 모두 존재
- 검증된 제공량 옵션 존재
- `last_verified_at` 존재
- `review_source_url` 존재
- `allergen_profile_verified=true`
- 주의 기준을 넘는 row는 `RECOMMENDABLE_WITH_CAUTION`과 사유 입력

### 1. 식품 사용 횟수 추적 (usage_count)

#### 데이터베이스 변경 (V13 마이그레이션)

```sql
ALTER TABLE food_catalog ADD COLUMN usage_count BIGINT NOT NULL DEFAULT 0;
```

#### 카운팅 규칙

1. **식단 기록 추가 시**
   - 기록에 포함된 모든 식품의 distinct 셋에 대해 `usage_count` +1
   - 같은 식품이 여러 번 추가되어도 1회만 카운팅

2. **식단 기록 삭제 시**
   - 기록에 포함된 모든 식품의 distinct 셋에 대해 `usage_count` -1
   - `usage_count`는 0 미만으로 떨어지지 않음 (max(0, current-1))

#### 구현 위치

```
backend/src/main/java/com/healthcare/domain/diet/
├── usecase/DietLogUseCases.java         # increment/decrement 로직
├── repository/FoodCatalogRepository.java # incrementUsageCount(), decrementUsageCount()
└── entity/FoodCatalog.java              # usageCount 필드
```

### 2. 검색 결과 정렬

`GET /api/v1/diet/catalog?searchTerm=<term>` 응답 정렬 순서:

1. **이름 접두사 매칭** (가장 높은 우선순위)
   - 정확한 prefix 매칭인 항목 먼저
   
2. **사용 횟수 내림차순** (usage_count DESC)
   - 사용자가 자주 기록한 식품 우선
   
3. **이름 가나다순** (name ASC)
   - 같은 사용 횟수면 이름 순서

#### 쿼리 예시

```java
// FoodCatalogRepository.searchAll()
SELECT f FROM FoodCatalog f
WHERE LOWER(f.name) LIKE :searchLower
ORDER BY 
  CASE WHEN LOWER(f.name) LIKE :prefixLower THEN 0 ELSE 1 END,
  f.usageCount DESC,
  f.name ASC
```

### 3. 사용자 직접 식품 등록 (Custom Food)

#### API 엔드포인트

```
POST /api/v1/diet/catalog
Content-Type: application/json
Authorization: Bearer {token} (선택 사항, 최초 사용자 구분용)

Request body:
{
  "name": "된장찌개",
  "category": "SOUP",
  "calories": 150,
  "protein": 8.0,
  "carbs": 20.0,
  "fat": 5.0
}

Response (201 Created):
{
  "id": 12345,
  "name": "된장찌개",
  "category": "SOUP",
  "calories": 150,
  "protein": 8.0,
  "carbs": 20.0,
  "fat": 5.0,
  "usageCount": 0,
  "createdByUserId": "user-uuid",
  "source": "CUSTOM"
}
```

#### 검색 API 공개화

```
GET /api/v1/diet/catalog?searchTerm=<term>
Authorization: (선택 사항)
```

- **이전 동작**: 로그인한 사용자의 커스텀 식품만 반환
- **현재 동작**: 모든 커스텀 식품 반환 (미인증 사용자도 검색 가능)

#### 입력 검증 및 정규화

##### Bean Validation (`CreateCustomFoodRequest`)

```java
@NotBlank
@Length(max = 100)
private String name;  // 공백, 100자 이내

@NotNull
@Min(0) @Max(9999)
private Integer calories;  // 0~9999

@Min(0) @Max(100)
private Double protein;  // 단백질 (선택 사항, 0~100)

@Min(0) @Max(100)
private Double carbs;    // 탄수화물 (선택 사항, 0~100)

@Min(0) @Max(100)
private Double fat;      // 지방 (선택 사항, 0~100)
```

##### 입력 정규화 (`FoodCatalogService.createCustomFood()`)

1. **NFC Unicode 정규화**
   - 한글 자모 조합 통일 (예: 된↓장 → 된장)
   
2. **연속 공백 축약**
   - 공백 2개 이상 → 1개로 축약
   - 양 끝 공백 제거 (trim)

3. **중복 검사**
   - 같은 이름+카테고리 조합이 이미 존재하면 409 Conflict
   - 앱 내에서 자동 응답 처리 (기존 항목 재사용)

#### 구현 위치

```
backend/src/main/java/com/healthcare/domain/diet/
├── controller/FoodCatalogController.java     # POST /api/v1/diet/catalog
├── service/FoodCatalogService.java           # createCustomFood 로직
├── dto/CreateCustomFoodRequest.java          # Bean Validation
├── dto/FoodCatalogResponse.java              # 응답 DTO
└── repository/FoodCatalogRepository.java      # 중복 검사 쿼리
```

## iOS 구현

### 1. 검색 결과 중복 제거

**문제**: 공공 데이터(식품디비) + 커스텀 식품 검색 시 같은 식품명이 중복되어 나타남

**해결책**: `displayName` 기준 `uniqued(by:)` 처리

```swift
// Date+Formatting.swift
extension Array {
  func uniqued(by keyPath: KeyPath<Element, some Hashable>) -> [Element] {
    var seen = Set<AnyHashable>()
    return filter { element in
      let key = AnyHashable(element[keyPath: keyPath])
      guard !seen.contains(key) else { return false }
      seen.insert(key)
      return true
    }
  }
}

// AddDietLogView
let uniqueCatalogResults = catalogResults.uniqued(by: \.displayName)
let uniqueExternalResults = externalResults.uniqued(by: \.displayName)
```

### 2. 직접 등록 화면 (`AddCustomFoodView`)

#### UI 구성

```
┌─────────────────────────────────┐
│ 직접 등록하기                     │
├─────────────────────────────────┤
│ 식품명                           │
│ [된장찌개            ]           │
│                                  │
│ 카테고리                         │
│ [SOUP           ▼]              │
│                                  │
│ 칼로리 (필수)                    │
│ [150            ] kcal          │
│                                  │
│ 단백질                           │
│ [8.0            ] g             │
│                                  │
│ 탄수화물                         │
│ [20.0           ] g             │
│                                  │
│ 지방                             │
│ [5.0            ] g             │
│                                  │
│ [등록]  [취소]                   │
└─────────────────────────────────┘
```

#### 기능

1. **자동 검색어 채움**
   - `.onAppear`에서 부모 ViewModel의 검색어를 name 필드에 복사
   
2. **입력 검증**
   - 식품명: 필수
   - 칼로리: 필수, 0~9999 범위
   - 기타 항목: 선택 사항, 0~100 범위
   
3. **등록 성공 시**
   - 새로운 식품을 검색 결과 맨 위에 prepend
   - 자동 선택 (탭 전환 없이 바로 기록)
   - dismiss

#### 구현 위치

```
ios/HealthCare/Features/Record/Diet/
├── Views/AddDietLogView.swift          # 빈 상태 UI + AddCustomFoodView 호출
├── Views/AddCustomFoodView.swift       # 직접 등록 폼 (신규)
├── ViewModels/AddDietLogViewModel.swift # submitCustomFood(), showCustomFoodForm
└── Models/DietModels.swift             # CreateCustomFoodRequest 모델
```

### 3. 탭 네비게이션 리셋

**문제**: 탭 전환 후 이전 탭으로 돌아오면 이전 화면 상태 유지

**해결책**: 탭 전환 시 NavigationStack을 루트로 리셋

```swift
// MainTabView.swift
@State private var recordTabPath = NavigationPath()
@State private var exploreTabPath = NavigationPath()

NavigationStack(path: $recordTabPath) {
  RecordHubView()
}
.onAppear {
  // 다른 탭에서 돌아올 때 리셋
  if !isRecordTabActive {
    recordTabPath = NavigationPath()
  }
}
```

## 테스트 커버리지

### 백엔드 단위 테스트

```java
// V23 카탈로그 메타데이터
- FoodCatalogServiceTest: createCustomFood_success_returnsCreatedFood()
- FoodImportServiceTest: importFood_fromPublicApi_savesAsUserCustomFood()
- RecommendationStatusTest: recommendationCandidateStatuses_includeRecommendableAndWithCaution()
- FoodCatalogSpecsTest: hasRecommendationCandidateStatus_includesOnlyCandidateStatuses()
- DietRecommendationCandidatePoolTest: load_alwaysAppliesRecommendationStatusFilter()
- StandardProcessedFoodImporterTest: importRows_createsProcessedFoodCatalogItem()
- StandardProcessedFoodImporterTest: importRows_updatesExistingFoodCatalogItem()
- StandardDishFoodImporterTest: importRows_createsDishFoodCatalogItem()
- MfdsFoodNutrientDbImporterTest: importRows_createsFoodNutrientDbCatalogItem()

// Phase 2 배치 파이프라인
- FoodCatalogImportBatchRunnerTest: importPages_resumesFromNextCheckpointAndMarksCompletedPages()
- FoodCatalogImportBatchRunnerTest: importPages_doesNotAdvanceCheckpointWhenPageFails()
- FoodCatalogPublicDataImportServiceTest: importStandardProcessedFoods_routesToProcessedFetcherAndImporter()
- JpaFoodCatalogImportCheckpointStoreTest: 체크포인트 저장/조회 E2E

// Phase 2 중복 후보 리포터
- FoodCatalogDuplicateCandidateReporterTest: report_emptyInput_returnsEmptyReport()
- FoodCatalogDuplicateCandidateReporterTest: report_singleEntry_noGroups()
- FoodCatalogDuplicateCandidateReporterTest: report_sameNormalizedName_formsGroup()
- FoodCatalogDuplicateCandidateReporterTest: report_differentNames_noGroups()
- FoodCatalogDuplicateCandidateReporterTest: report_threeEntriesSameName_allInOneGroup()
- FoodCatalogDuplicateCandidateReporterTest: report_multipleNameGroups_separateGroups()
- FoodCatalogDuplicateCandidateReporterTest: report_nullNameKo_usesNameForNormalization()
- FoodCatalogDuplicateCandidateReporterTest: report_groupHasNormalizedKey()
- FoodCatalogDuplicateCandidateReporterTest: report_parenthesesDifference_normalizedToSameKey()

// FoodCatalogServiceTest
- createCustomFood_이미_존재하는_이름_카테고리_조합_중복_거절()
- createCustomFood_NFC_정규화()
- createCustomFood_연속_공백_축약()

// DietLogUseCasesTest
- addDietLog_식품별_usage_count_increment()
- removeDietLog_식품별_usage_count_decrement()
- removeDietLog_usage_count_최소_0_유지()
```

### iOS 단위 테스트

```swift
// AddDietLogViewModelTests
- submitCustomFood_성공()
- submitCustomFood_네트워크_오류()
- catalogResults_uniqued_by_displayName()
```

## 마이그레이션 가이드

### 기존 데이터베이스 업그레이드

1. **V13 마이그레이션 실행**
   ```bash
   # Flyway가 자동으로 실행됨
   docker compose up -d postgres redis
   ```

2. **V23 마이그레이션 실행**
   - Flyway가 `V23__food_catalog_source_recommendation_fields.sql`을 자동 적용합니다.
   - 기존 사용자 커스텀 식품은 검증 전 추천 제외를 위해 `SEARCH_ONLY`로 백필됩니다.

3. **V24 마이그레이션 실행**
   - Flyway가 `V24__food_catalog_import_checkpoints.sql`을 자동 적용합니다.
   - 공공데이터 배치 적재 시 source별 마지막 완료 페이지를 추적하는 `food_catalog_import_checkpoints` 테이블이 생성됩니다.

4. **V25 seed 큐레이션 보정 마이그레이션 실행**
   - V23은 이미 적용됐을 수 있으므로 직접 수정하지 않습니다.
   - V25에서 기존 seed 전체를 `SEARCH_ONLY`로 낮춘 뒤, 명시 큐레이션 allowlist만 `RECOMMENDABLE`로 승격합니다.
   - V25 seed 보정에서는 `RECOMMENDABLE_WITH_CAUTION`을 사용하지 않습니다. 주의 추천은 출처와 사유가 더 명확한 브랜드 CSV 등 운영 검수 데이터에 우선 적용합니다.
   - V25는 마이그레이션 SQL 내부의 inline `VALUES` allowlist로 처리합니다. 현재 seed는 V4/V12 시점에 생성되어 `food_code`가 없으므로 `source = 'SEED' AND name_ko AND category` 조합으로 매칭합니다.
   - 이번 V25에는 seed synthetic `food_code` 백필을 포함하지 않습니다. seed identity 체계가 필요해지면 별도 마이그레이션에서 `seed:<normalized-name>` 같은 규칙을 설계합니다.

5. **초기 usage_count 계산 (선택 사항)**
   ```sql
   -- 기존 식단 기록을 기반으로 usage_count 초기화
   -- (현재는 0부터 시작, 향후 히스토리 분석 시 필요)
   ```

## 알려진 제약 사항 및 향후 개선

1. **식품 승인 프로세스 미실장**
   - 사용자 등록 식품이 즉시 공개됨
   - 향후: 관리자 검수 및 거절 기능 추가

2. **AI 추정 결과 표시 미완료**
   - AI로 추정된 영양성분 표시 UI 미구현
   - 향후: "AI 추정값" 배지 + disclaimer 텍스트 추가

3. **공공데이터 사전 적재 파이프라인 구현 완료, local 검증 충분**
   - 배치 파이프라인(importer, fetcher, runner, checkpoint) 구현 완료
   - `POST /api/v1/admin/diet/catalog/import/*` 관리자 API로 실행 가능
   - local에서는 smoke/제한 배치와 대표 대량 적재 장애 케이스를 확인했으므로 모든 row를 끝까지 적재하지 않음
   - staging/운영 전량 적재가 필요하면 `processed-foods` → `dish-foods` → `nutrient-db` 순서로 진행하고, source별 `exhausted=true`가 나올 때까지 반복 실행
   - 신규 공공데이터 항목은 기본 `SEARCH_ONLY`로 유지하며 추천 후보 승격은 별도 큐레이션 작업으로 분리

4. **주의 상태 iOS 표시 후속 작업**
   - 백엔드 응답 모델은 확정됨: `RECOMMENDABLE_WITH_CAUTION`은 추천 후보에 포함되며, `RecommendedFoodEntry.caution`에 `recommendation_reason`을 담아 노출합니다.
   - 일반 `RECOMMENDABLE` 식품의 `caution`은 `null`입니다. iOS 화면 표시는 후속 UI 작업에서 처리합니다.

## 성능 고려사항

### 데이터베이스 인덱싱

```sql
-- V13 마이그레이션에 포함
CREATE INDEX idx_food_catalog_usage
  ON food_catalog (usage_count DESC, name_ko ASC)
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_food_catalog_custom_name_category
  ON food_catalog (LOWER(name_ko), category)
  WHERE deleted_at IS NULL AND is_custom = TRUE;
```

V23 마이그레이션에 포함:

```sql
CREATE INDEX idx_food_catalog_recommendation_status
  ON food_catalog(recommendation_status)
  WHERE deleted_at IS NULL;

-- 검색 품질이 부족할 경우
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- CREATE INDEX idx_food_catalog_normalized_name_trgm
--   ON food_catalog USING gin (normalized_name gin_trgm_ops);
```

### 캐싱 전략

- 식품 검색 결과는 캐싱하지 않음 (usage_count가 자주 변경됨)
- Redis 캐시는 일일 매크로 합계 등에만 사용

## 참고 문서

- [DB 스키마](./DB_SCHEMA.md)
- [API 설계](./API_DESIGN.md)
- [CURRENT_STATUS.md](./CURRENT_STATUS.md) — Phase 3 진행률 100%
