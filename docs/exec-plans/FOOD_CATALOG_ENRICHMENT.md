# 식품 데이터(food_catalog) 보강 실행 계획

작성일: 2026-06-04

개정일: 2026-06-11

상태: 1단계 완료, 2단계 완료(실제 API smoke 검증 대기), 3단계 완료, 4단계 완료, 5단계 완료

대상: 백엔드, 데이터 운영, 추천 엔진, iOS 검색/기록 UX

관련: `docs/product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md`

작업 브랜치: `feat/allegen-recommendation`

> 알러젠 식단 추천과 식품 카탈로그 강화 작업은 `feat/allegen-recommendation` 브랜치에서만 진행한다. `dev`에는 직접 커밋하지 않고, 검증된 변경만 PR/머지로 반영한다.

> 본 계획은 AI 영양 추정이 아니라, 공공 권위 데이터와 검수된 브랜드 공식 메뉴로 `food_catalog`의 검색/기록/추천 품질을 높이는 작업을 다룬다.

---

## 1. 결정 요약

1. `food_catalog`는 식단 기록 검색, 식단 기록 영양 계산, 식단 추천 후보 풀의 **공통 기반**으로 운영한다.
2. 현재 사용 중인 전국통합식품영양성분정보 표준데이터 2종(가공식품, 음식)은 폐기하지 않고, 내부 카탈로그의 주요 공공데이터 소스로 유지한다.
3. 식약처 식품영양성분DB정보 API(`FoodNtrCpntDbInfo02`, 15127578)는 기존 2종을 무조건 대체하는 새 메인소스가 아니라, 중복률과 필드 커버리지를 확인한 뒤 **보강/병합 소스**로 사용한다.
4. 원재료성식품 표준데이터(15100065)는 v1 필수 범위에서 제외하고 후속 검토한다.
5. 버거킹, BBQ, 서브웨이, 샐러디, 프레퍼스 등 상위 브랜드 공식 메뉴는 v1에서 자동 크롤링하지 않고 **관리자 CSV/수동 검수**로 일부 적재한다.
6. 검색/기록 가능 여부와 추천 가능 여부를 분리한다. 모든 카탈로그 항목이 추천 후보가 되지는 않는다.
7. 추천 가능 여부는 브랜드 단위가 아니라 메뉴/식품 단위의 추천 적합성 게이트로 판단한다.
8. 사용자 검색/추천 시점의 외부 API 실시간 호출은 제거하거나 관리자/배치 경로로 강등한다.
9. v1 추천 후보는 명시적으로 큐레이션한 seed와 `BRAND_OFFICIAL` CSV 검수 항목 중심으로 제한한다. 기존 seed 전체를 자동 추천 후보로 보지 않으며, 공공데이터 배치 적재 항목은 기본 `SEARCH_ONLY`로 두고 대량 자동 승격하지 않는다.
10. 보수적인 allowlist로 후보 풀이 얇아지는 문제를 막기 위해 seed 큐레이션은 최소 후보 규모와 카테고리별 하한을 통과해야 한다. 추천 엔진은 같은 후보 풀에서도 날짜 기준으로 우선순위를 회전해 매번 동일한 식단이 반복되는 상황을 줄인다.

---

## 2. 배경과 현황 진단

| 항목 | 현황 | 한계 |
|---|---|---|
| 내부 시드 | `food_catalog`에 기본 식품 시드 존재 | 실제 사용자가 검색할 한식/외식/브랜드 메뉴 커버리지 부족 |
| 외부 검색 | 공공데이터 API를 검색 시점에 호출하는 경로 존재 | 지연, 장애, 트래픽 제한, 결과 비영속 |
| 추천 | `food_catalog` 기반 추천 후보 조회가 DB 필터링 방향으로 개선됨 | 추천 가능한 식품과 단순 기록용 식품의 구분이 아직 약함 |
| 필드 | 10종 영양소 + 카테고리 + 사용 횟수 중심 | 출처, 식품코드, 브랜드, 제공량, 검수 상태, 추천 상태 부족 |
| 사용자 커스텀 | 사용자가 직접 등록 가능 | 추천 후보로 쓰기에는 검증 수준이 낮음 |

핵심 전환 목표:

> 실시간 외부 검색 의존을 줄이고, 공공데이터와 검수된 브랜드 메뉴를 사전 적재한 내부 카탈로그를 검색/기록/추천의 기준 데이터로 만든다.

---

## 3. 데이터원과 역할

### 3.1 채택 데이터원

| 역할 | 소스 | 식별자 | 사용 방식 | 비고 |
|---|---|---|---|---|
| 공공 기본 소스 | 전국통합식품영양성분정보 가공식품 표준데이터 | 15100066 | 현재 사용 중인 API/데이터 유지, 내부 DB 사전 적재 | 가공식품 검색 커버리지 |
| 공공 기본 소스 | 전국통합식품영양성분정보 음식 표준데이터 | 15100070 | 현재 사용 중인 API/데이터 유지, 내부 DB 사전 적재 | 외식/일반 음식 커버리지 |
| 보강/비교 소스 | 식약처 식품영양성분DB정보 API | 15127578 | 필드 커버리지/중복 분석 후 배치 병합 | `FoodNtrCpntDbInfo02`, 1회 섭취 참고량/식품중량/식품코드 보유 |
| 브랜드 보강 | 브랜드 공식 영양정보 | 자체 검수 | 관리자 CSV 수동 적재 | 버거킹, BBQ, 서브웨이, 샐러디, 프레퍼스 등 상위 브랜드 일부 |
| 후속 검토 | 원재료성식품 표준데이터 | 15100065 | v1 필수 제외 | 식재료 단위 추천/장보기 단계에서 재검토 |

### 3.2 `FoodNtrCpntDbInfo02`의 위치

`FoodNtrCpntDbInfo02`는 새 단일 메인소스가 아니다. 기존에 이미 쓰는 표준데이터 2종이 있으므로 다음 순서로 판단한다.

1. 기존 가공식품/음식 표준데이터와 식품명/제조사/영양값 기준 중복률 확인
2. `FoodNtrCpntDbInfo02`가 더 안정적으로 제공하는 필드 확인
3. 동일 식품은 source priority와 최신성 기준으로 병합
4. 기존 2종에 없는 식품 또는 제공량/식품코드 보강이 가능한 항목만 추가/갱신

### 3.3 브랜드 공식 메뉴의 위치

공공데이터만으로는 사용자가 실제로 찾는 프랜차이즈 메뉴 커버리지가 부족할 수 있다. 따라서 상위 브랜드 일부를 직접 관리한다.

예시:

- 검색/기록 중심: 와퍼세트, 황금올리브치킨, 브랜드 음료/사이드
- 추천 후보 가능: 서브웨이 로스트치킨 샌드위치, 샐러디 샐러드/웜볼, 프레퍼스 닭가슴살 식단

단, 추천 여부는 브랜드가 아니라 메뉴 단위 영양 기준으로 판단한다.

---

## 4. 검색/기록/추천 사용 정책

### 4.1 공통 카탈로그 원칙

사용자는 식단 기록 시 `food_catalog`에 있는 식품을 검색하고 기록할 수 있다. 강화된 카탈로그는 추천뿐 아니라 사용자의 일반 식단 기록 검색에도 그대로 쓰인다.

### 4.2 추천 후보 상태

| 상태 | 의미 | 검색/기록 | 추천 |
|---|---|---:|---:|
| `SEARCH_ONLY` | 검색/기록은 가능하지만 추천 후보 제외 | O | X |
| `RECOMMENDABLE` | 일반 추천 후보 | O | O |
| `RECOMMENDABLE_WITH_CAUTION` | 나트륨/당류/포화지방 등 주의 표시가 필요한 후보 | O | O(주의 표시) |
| `DISABLED` | 데이터 불완전, 검수 실패, 만료 등으로 비활성 | X | X |

예시 정책:

| 식품/메뉴 | 검색/기록 | 추천 |
|---|---:|---:|
| 와퍼세트 | O | 보통 `SEARCH_ONLY` 또는 `RECOMMENDABLE_WITH_CAUTION` |
| 황금올리브치킨 | O | 보통 `SEARCH_ONLY` |
| 서브웨이 로스트치킨 샌드위치 | O | 기준 통과 시 `RECOMMENDABLE` |
| 샐러디 샐러드/웜볼 | O | 기준 통과 시 `RECOMMENDABLE` |
| 프레퍼스 닭가슴살 식단 | O | 기준 통과 시 `RECOMMENDABLE` |
| 사용자 커스텀 식품 | O | 기본 `SEARCH_ONLY` 또는 검증 전 `UNKNOWN` 취급 |

---

## 5. 스키마 보강

`food_catalog`에 다음 필드를 추가하는 방향으로 설계한다. 실제 구현 시 기존 엔티티와 마이그레이션 번호를 확인해 최소 변경으로 반영한다.

| 컬럼 | 타입 | 용도 |
|---|---|---|
| `food_code` | VARCHAR(60) | 공공데이터 식품 코드, upsert/중복 제거 |
| `source` | VARCHAR(40) | 대표 출처 |
| `source_detail` | VARCHAR(120) | 세부 출처/API/파일명 |
| `brand_name` | VARCHAR(150) | 브랜드/프랜차이즈명 |
| `maker` | VARCHAR(150) | 제조사/업체명 |
| `serving_size_g` | DOUBLE PRECISION | 1회 제공량 g |
| `serving_reference` | VARCHAR(80) | "1개", "1회", "210g" 등 표시용 기준 |
| `recommendation_status` | VARCHAR(40) | 추천 후보 상태 |
| `recommendation_reason` | VARCHAR(255) | 주의/제외 사유 |
| `data_version` | VARCHAR(80) | 적재 배치/원본 버전 |
| `last_verified_at` | TIMESTAMP | 마지막 검수/확인 시각 |

`source` 후보:

- `SEED`
- `MFDS_STANDARD_PROCESSED`
- `MFDS_STANDARD_DISH`
- `MFDS_FOOD_NUTRIENT_DB`
- `BRAND_OFFICIAL`
- `USER_CUSTOM`

권장 인덱스:

```sql
CREATE UNIQUE INDEX uq_food_catalog_source_food_code
  ON food_catalog (source, food_code)
  WHERE food_code IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_food_catalog_recommendation_status
  ON food_catalog (recommendation_status)
  WHERE deleted_at IS NULL;
```

검색 품질이 부족하면 `normalized_name` 컬럼과 PostgreSQL `pg_trgm` 인덱스를 추가 검토한다.

### 5.1 세트 메뉴 후속 모델

세트 메뉴는 v1에서 단일 카탈로그 항목으로 적재하되, v2에서 구성품 기반 계산을 위해 별도 테이블을 검토한다.

- `food_bundles`
- `food_bundle_components`

예: 와퍼세트 = 와퍼 + 프렌치프라이 + 콜라

---

## 6. 적재 파이프라인

### 6.1 원칙

외부 데이터는 사용자 검색/추천 런타임에서 직접 호출하지 않는다. 배치 또는 관리자 작업으로 내부 DB에 적재한 뒤, 앱 기능은 내부 `food_catalog`만 조회한다.

### 6.2 공공데이터 적재기

| 적재기 | 역할 |
|---|---|
| `StandardProcessedFoodImporter` | 15100066 가공식품 표준데이터 적재 |
| `StandardDishFoodImporter` | 15100070 음식 표준데이터 적재 |
| `MfdsFoodNutrientDbImporter` | 15127578 `FoodNtrCpntDbInfo02` 분석/보강 적재 |

공통 처리:

1. 원본 행 수, 성공/실패 수, 필수 영양값 결측 수 기록
2. 식품명 정규화(NFC, 공백 축약, 소문자 검색 키)
3. 카테고리 매핑
4. 칼로리/탄단지 등 핵심 영양값 검증
5. `source + food_code` 기준 upsert
6. 동일 식품 추정 중복은 별도 리포트로 남기고 자동 병합은 보수적으로 처리

### 6.3 브랜드 공식 메뉴 CSV 적재

v1에서는 자동 크롤링을 하지 않는다. 관리자 CSV 템플릿으로 수집/검수한 데이터만 `BRAND_OFFICIAL`로 적재한다.

v1에서 브랜드 공식 메뉴의 추천 상태 변경은 CSV 재업로드/재적재를 기준 경로로 둔다. 개별 식품의 추천 상태를 직접 수정하는 관리자 API는 원본 CSV와 DB 상태가 갈라질 수 있으므로 즉시 만들지 않는다. 운영 중 CSV 재적재가 과하게 무겁다는 근거가 쌓이면, 변경 이력과 원본 충돌 정책을 함께 설계한 뒤 후속으로 검토한다.

필수 컬럼:

- `brand_name`
- `menu_name`
- `category`
- `nutrition_basis` (`PER_SERVING` 또는 `PER_100G`)
- `serving_size_g`
- `calories`
- `protein`
- `carbs`
- `fat`
- `sodium`
- `sugar`
- `saturated_fat`
- `source_url`
- `last_verified_at`
- `recommendation_status`
- `recommendation_reason`

---

## 7. 추천 적합성 게이트

추천 후보는 `food_catalog` 전체가 아니라 추천 적합성 게이트를 통과한 일부 항목만 사용한다. 다만 Phase 4의 런타임 범위는 `recommendation_status` 계약과 추천 응답 노출까지로 제한한다. 나트륨/당류/포화지방의 자동 판정 기준값 산정은 데이터 운영 작업으로 분리해, CSV/관리자 검수 정책에서 먼저 검증한 뒤 후속 자동화 여부를 결정한다.

초기 판단 기준:

| 상태 | 기준 |
|---|---|
| `RECOMMENDABLE` | 제공량 기준 영양값이 완전하고, 운영 검수 기준상 일반 추천에 적합 |
| `RECOMMENDABLE_WITH_CAUTION` | 추천 가능하지만 나트륨, 당류, 포화지방 등 운영 검수 기준상 주의 표시가 필요 |
| `SEARCH_ONLY` | 검색/기록에는 유용하지만 추천 목표와 맞지 않거나 영양/알러젠 정보가 부족 |
| `DISABLED` | 결측/오류/검수 실패/만료 |

추천 런타임 처리:

1. DB WHERE 절에서 `recommendation_status IN ('RECOMMENDABLE', 'RECOMMENDABLE_WITH_CAUTION')`
2. 알러젠/기피식품/목표 칼로리/목표 단백질 조건으로 1차 필터링
3. seed/브랜드 추천 후보 풀이 최소 규모를 충족하는지 운영 검증
4. 후보를 200~500개 수준으로 제한
5. 추천 엔진에서 끼니 구성 최적화와 날짜 기반 deterministic rotation 적용
6. `RECOMMENDABLE_WITH_CAUTION`은 응답에 주의 근거 표시 가능하도록 유지

seed allowlist 최소 기준:

| 구분 | 최소 후보 수 |
|---|---:|
| 전체 추천 가능 seed | 40~60개 이상 |
| 단백질 | 10개 이상 |
| 곡류/주식 | 8개 이상 |
| 채소 | 10개 이상 |
| 과일 | 6개 이상 |
| 유제품/간식 대체 | 4개 이상 |

이 기준을 만족하지 못하면 추천 엔진을 임시로 넓히기보다 seed/브랜드 후보를 먼저 보강한다. 날짜 기반 회전은 추천 엔진의 선택 전략에만 적용하고, 어떤 식품이 추천 가능한지는 큐레이션 상태가 계속 결정한다. 최근 추천/최근 기록 기반 중복 억제는 v2 품질 개선으로 분리한다.

---

## 8. 실시간 외부 검색 정책

기존 외부 검색 API는 사용자 경험의 핵심 경로에서 제거한다.

- 사용자 검색: 내부 `food_catalog` 조회
- 사용자 기록: 내부 `food_catalog` 또는 사용자 직접 등록
- 추천: 내부 `food_catalog`의 추천 가능 후보만 조회
- 외부 API: 관리자 배치, 데이터 분석, 최신 메뉴 보강 후보 수집 용도

이 정책의 목적:

- API 장애가 사용자 검색/추천에 영향을 주지 않게 함
- 응답 지연과 트래픽 제한 리스크 제거
- 동일 검색어 결과의 재현성 확보
- 출처/검수/추천 상태를 내부에서 통제

---

## 9. 운영 비용과 성능

### 9.1 비용

인프라 비용보다 운영 비용이 더 크다.

| 비용 항목 | 판단 |
|---|---|
| 공공데이터 API 비용 | 무료 또는 낮음. 트래픽 제한 관리 필요 |
| DB 저장 비용 | 수만~수십만 행 수준은 현재 서비스 규모에서 부담 작음 |
| 검색 인덱스 비용 | `pg_trgm` 도입 시 인덱스 크기 증가. 감당 가능 |
| 브랜드 메뉴 운영 비용 | 가장 큼. 수집, 출처 확인, 검수, 갱신, 약관 확인 필요 |

### 9.2 성능

검색 성능:

1. 초기: `name`, `category`, `usage_count` 기반 검색
2. 개선: `normalized_name` 추가
3. 필요 시: PostgreSQL `pg_trgm` 기반 유사 검색

추천 성능:

1. 전체 `findAll()` 금지
2. `recommendation_status`, 카테고리, 알러젠/기피 조건, 기본 영양 조건을 DB WHERE 절로 먼저 적용
3. 추천 엔진에는 제한된 후보만 전달

---

## 10. 구현 순서

### 현재 작업 목록(2026-06-10)

| 단계 | 상태 | 완료 내용 | 다음 액션 |
|---|---|---|---|
| 0단계 데이터 프로파일링 | 1차 완료 | 공공데이터 3종 샘플 비교, 필드 매핑, source priority 초안 정리 | 전체 crawl 프로파일러는 importer 단계에서 진행 |
| 1단계 스키마 보강 | 완료 | V23 마이그레이션, `FoodCatalog` 메타데이터, source/recommendation enum, 응답 DTO, repository 반영 | 운영 DB 적용 전 Flyway 실행 환경 확인 |
| 4단계 추천 게이트 적용 | **완료** | 추천 후보 조회 필터 + `RecommendedFoodEntry.caution` 필드로 주의 사유 응답 노출 | — |
| 2단계 공공데이터 배치 적재 | **완료** | row importer, page fetcher, 배치 runner, 재시작 체크포인트, rate limit 훅, 중복 후보 리포터, 관리자 API 구현 | 실제 공공 API smoke 검증(API 키 설정 후 수동 실행) |
| 3단계 브랜드 CSV 적재 | **완료** | `BrandMenuCsvImporter`, 관리자 CSV 업로드 API, 템플릿 CSV | — |
| 5단계 검색/기록 경로 정리 | **완료** | iOS 외부 API 경로 전면 제거(`ExternalFoodResult`, `ImportFoodRequest`, `FoodDataSource` 삭제, `DietFoodSearching` 프로토콜 단순화), 내부 카탈로그 단일 경로 고정. `ExternalFoodController`는 관리자 도구 전용으로 유지 | — |
| 6단계 테스트와 운영 검증 | 진행 중 | V23 필드, 커스텀 기본값, 추천 상태, 배치 runner, 중복 리포터, V25 seed 큐레이션 보정 테스트 완료 | DB가 있는 환경에서 Flyway V23~V25 적용 검증, 실제 API smoke 검증 |

### 0단계: 데이터 프로파일링

1. 현재 사용 중인 가공식품/음식 표준데이터 2종의 필드와 응답 샘플 재확인
2. `FoodNtrCpntDbInfo02` 샘플/전체 페이지 호출
3. 세 데이터 간 중복률, 누락률, 브랜드/외식 메뉴 커버리지 비교
4. 상위 브랜드 후보와 공식 영양정보 제공 형식 조사

산출물:

- 데이터 비교 리포트
- 필드 매핑표
- source priority 초안

진행 메모(2026-06-09):

- 0단계 1차 산출물: `docs/references/FOOD_CATALOG_DATA_PROFILING_2026-06-09.md`
- API 총 건수, 첫 페이지 필드, 대표 검색어(`김치찌개`, `와퍼`, `닭가슴살`, `샐러드`) 기준 커버리지와 핵심 영양소 결측을 확인했다.
- `FoodNtrCpntDbInfo02`는 `AMT_NUM1`, `AMT_NUM3`, `AMT_NUM4`, `AMT_NUM6`, `AMT_NUM7`, `AMT_NUM8`, `AMT_NUM13`까지 핵심 영양소 매핑을 1차 확인했다.
- 지방산/콜레스테롤 등 후반 `AMT_NUM*` 매핑은 출력 메세지 파일 대조 후 importer 구현 단계에서 확정한다.
- 전체 페이지 순회는 API 트래픽과 재시작 처리가 필요하므로 Phase 2 importer/프로파일러 작업에 포함한다.

### 1단계: 스키마 보강

1. `food_catalog` 출처/브랜드/제공량/추천 상태 필드 추가
2. `source + food_code` 유니크 인덱스 추가
3. 추천 후보 조회 인덱스 추가
4. 필요 시 `normalized_name`/`pg_trgm` 도입

진행 메모(2026-06-10):

- `V23__food_catalog_source_recommendation_fields.sql`로 출처, 브랜드/제조사, 제공량, 추천 상태, 데이터 버전, 검수 시각 필드를 추가했다.
- 기존 seed 전체를 `RECOMMENDABLE`로 자동 백필하는 정책은 폐기한다. V23은 이미 적용됐을 수 있으므로 직접 수정하지 않고, V25 보정 마이그레이션에서 seed 전체를 `SEARCH_ONLY`로 낮춘 뒤 명시 큐레이션 allowlist만 `RECOMMENDABLE`로 승격한다. V25 seed 보정에서는 `RECOMMENDABLE_WITH_CAUTION`을 사용하지 않는다.
- V25 seed 보정은 일회성 정적 데이터 보정이므로 마이그레이션 SQL 내부의 inline `VALUES` allowlist로 처리한다. 현재 seed는 V4/V12 시점에 생성되어 `food_code`가 없으므로 `source = 'SEED' AND name_ko AND category` 조합으로 매칭한다. 이번 V25에는 seed synthetic `food_code` 백필을 포함하지 않는다. seed identity 체계가 필요해지면 별도 마이그레이션에서 `seed:<normalized-name>` 같은 규칙을 설계한다.
- `V25__seed_recommendation_curation.sql`을 추가해 seed 전체 하향과 allowlist 승격을 구현했다. 전체 40~60개 범위와 카테고리별 최소 후보 수는 `FoodCatalogSeedCurationMigrationTest`로 검증한다.
- `FoodCatalogSource`, `RecommendationStatus` enum과 `FoodCatalog` 엔티티, `FoodCatalogResponse` 응답 DTO 반영을 완료했다.
- 사용자 직접 등록과 기존 외부 import 경로는 `USER_CUSTOM + SEARCH_ONLY`로 저장되도록 조정했다.
- `FoodCatalogRepository.findBySourceAndFoodCode()`를 추가해 `source + food_code` 기반 적재/upsert 경로를 준비했다.
- `source + food_code` 부분 유니크 인덱스와 `recommendation_status` 인덱스를 추가했다.
- `normalized_name`/`pg_trgm`은 검색 품질 개선 시점까지 보류한다.

### 2단계: 공공데이터 배치 적재

1. 표준데이터 2종 importer 구현
2. `FoodNtrCpntDbInfo02` importer 구현
3. 정규화/upsert/중복 리포트 구현
4. 적재 결과 검증 테스트 작성

진행 메모(2026-06-10):

- `StandardProcessedFoodImporter`를 추가해 15100066 가공식품 표준데이터 row를 `MFDS_STANDARD_PROCESSED` 출처로 적재한다.
- `StandardDishFoodImporter`를 추가해 15100070 음식 표준데이터 row를 `MFDS_STANDARD_DISH` 출처로 적재한다. `restNm` 성격의 값은 `brand_name`/`maker`에 보존한다.
- `MfdsFoodNutrientDbImporter`를 추가해 `FoodNtrCpntDbInfo02` row를 `MFDS_FOOD_NUTRIENT_DB` 출처로 적재한다.
- 공통 동작은 `source + food_code` 기준 신규 생성/기존 항목 갱신이며, 필수값(`food_code`, 식품명, 열량)이 없으면 skip 처리한다.
- 공공데이터 재적재는 원본 메타데이터와 영양값만 갱신하고, 기존 항목의 추천 검수 상태(`recommendation_status`, `recommendation_reason`)는 보존한다.
- 공공데이터로 신규 적재되는 항목은 기본 `SEARCH_ONLY`로 둔다. v1에서는 공공데이터 항목을 추천 후보로 대량 자동 승격하지 않고, 필요 시 `source + food_code` 기준 큐레이션 오버레이 CSV를 별도 운영 작업으로 검토한다.
- `StandardProcessedFoodPageFetcher`, `StandardDishFoodPageFetcher`, `MfdsFoodNutrientDbPageFetcher`를 추가해 공공데이터 API 페이지 응답을 importer row로 변환한다.
- `FoodCatalogImportBatchRunner`는 체크포인트의 다음 페이지부터 `fetcher -> importer -> checkpoint 저장` 순서로 순회한다. 페이지 처리 중 예외가 나면 해당 페이지는 완료로 기록하지 않아 재시작 시 같은 페이지부터 다시 처리한다.
- `V24__food_catalog_import_checkpoints.sql`와 `JpaFoodCatalogImportCheckpointStore`를 추가해 source별 마지막 완료 페이지를 저장한다.
- `FixedDelayFoodCatalogImportPageThrottle`를 추가했다. 기본 delay는 0ms이며 `app.food-api.import-page-delay-millis`로 페이지 사이 대기 시간을 조정할 수 있다.
- `FoodCatalogPublicDataImportService`를 추가해 15100066, 15100070, `FoodNtrCpntDbInfo02` 적재 경로를 한 진입점에서 실행할 수 있게 했다.
- `FoodCatalogDuplicateCandidateReporter`를 추가해 정규화 이름(`[공백, -, _, /, (), （）]` 제거 후 소문자) 기준으로 동일 추정 중복 그룹을 찾는다. 자동 병합 없이 리포트만 반환한다.
- `FoodCatalogDuplicateReportService`가 DB에서 비커스텀 카탈로그를 로드해 reporter에 전달하고, 컨트롤러 응답 DTO로 매핑한다.
- `FoodCatalogAdminController`를 추가해 운영 실행 트리거 3종(`POST /api/v1/admin/diet/catalog/import/{processed-foods|dish-foods|nutrient-db}`)과 중복 후보 리포트(`GET /api/v1/admin/diet/catalog/dedup/report`)를 제공한다.
- 관리자 카탈로그 작업은 일반 사용자 JWT 인증과 별도로 `X-Admin-Token` operation token을 요구한다. `app.admin.operation-token`이 비어 있으면 fail-closed로 거부된다.
- `FoodCatalogAdminOperations`를 추가해 관리자 카탈로그 작업의 권한 검증, `pageSize`/`maxPages` 상한, 실행 로그, 실제 작업 호출을 한 module에 모았다.
- 남은 작업은 실제 공공 API smoke 검증(API 키 설정 후 수동 실행)과 운영 rate limit 값 확정이다.

### 3단계: 브랜드 공식 메뉴 CSV 적재

1. CSV 템플릿 정의
2. 관리자 검수 플로우 정의
3. 상위 브랜드 일부 수동 입력
4. `BRAND_OFFICIAL` 출처로 적재

진행 메모(2026-06-11):

- `BrandMenuCsvRow`를 추가해 CSV 행의 필드 구조를 정의했다. 헤더 배열은 `BrandMenuCsvRow.HEADERS`로 노출한다.
- `BrandMenuCsvImporter`를 추가해 Apache Commons CSV로 UTF-8 CSV를 파싱하고, `nutrition_basis`에 따라 입력 영양값을 내부 저장 기준인 100g당 값으로 정규화한 뒤 `BRAND_OFFICIAL` 출처로 upsert한다.
- `nutrition_basis = PER_SERVING`이면 `serving_size_g`가 필수이며, 입력 영양값은 1회 제공량 전체 기준이다.
- `nutrition_basis = PER_100G`이면 입력 영양값은 이미 100g당 기준이다. 공식 전체 제공량이 있으면 `serving_size_g`를 보존하고, 없으면 비워 둔다.
- CSV 헤더가 템플릿과 다르면 파일을 거절한다. 개별 row의 필수값/숫자 형식/제공량 기준이 잘못되면 DB에 저장하지 않고 `rejectedRows`에 row 번호, 필드, 사유를 반환한다.
- `food_code`는 `brandName:menuName` 소문자 형식으로 생성한다. `source + food_code` 유니크 인덱스를 활용해 중복 없이 upsert한다.
- `recommendation_status` 컬럼으로 메뉴 단위 추천 여부를 제어한다. 알 수 없는 값은 `SEARCH_ONLY`로 폴백한다.
- `recommendation_status = RECOMMENDABLE_WITH_CAUTION`은 `recommendation_reason`을 필수로 요구한다. 다른 상태에서는 추천 사유를 저장하지 않아 추천 큐레이션 값 객체의 불변 조건과 맞춘다.
- v1에서 브랜드 공식 메뉴의 추천 상태 변경은 CSV 재업로드/재적재로 처리한다. 개별 카탈로그 큐레이션 수정 API는 원본 CSV와 DB 상태 불일치 리스크가 있으므로 후속 운영 필요가 확인될 때 검토한다.
- `FoodCatalogAdminController`에 `POST /api/v1/admin/diet/catalog/import/brand-csv` 엔드포인트를 추가해 `multipart/form-data` CSV 업로드를 받는다.
- `docs/references/brand_menu_csv_template.csv`에 헤더·예시 행 포함 CSV 템플릿을 추가했다.
- `build.gradle.kts`에 `org.apache.commons:commons-csv:1.12.0` 의존성을 추가했다.
- architecture 정리로 `FoodCatalogIngestService`를 추가했다. 소스별 importer는 원본 row를 `FoodCatalogIngestCandidate`로 변환하고, 공통 적재 모듈이 `source + food_code` upsert, 생성/갱신/거절 집계, 추천 큐레이션 보존/교체 정책을 처리한다.
- phase 3 architecture 정리로 `FoodCatalogIdentity`를 추가했다. 브랜드 공식 메뉴 `food_code`, 브랜드 인식 중복 후보 키, 표시 이름 정규화를 한곳으로 모아 dedup false positive를 줄이고 이후 검색 정규화에서 재사용할 수 있게 했다.

### 4단계: 추천 게이트 적용

1. 추천 상태 enum/정책 구현
2. 추천 후보 조회에 `recommendation_status` 조건 추가
3. 주의 상태 응답 모델 확정
4. 추천 엔진 테스트 보강

진행 메모(2026-06-10):

- 추천 상태 enum과 저장 모델은 1단계에서 선반영되었다.
- `RecommendationStatus.recommendationCandidateStatuses()`와 `FoodCatalogSpecs.hasRecommendationCandidateStatus()`로 추천 후보 상태 조회 레이어를 분리했다.
- `DietRecommendationCandidatePoolTest`로 `SEARCH_ONLY`, `DISABLED`가 추천 후보에서 제외되는 조건을 검증했다.
- `RECOMMENDABLE_WITH_CAUTION`의 주의 사유는 `RecommendationCuration.cautionForResponse()`를 거쳐 추천 응답의 `RecommendedFoodEntry.caution` 필드로 노출한다. 일반 `RECOMMENDABLE` 식품의 `caution`은 `null`이다.
- `DietRecommendationCandidate` 값 객체를 추가해 Engine 입력에서 `FoodCatalog`, 알러젠 태그 맵, `strictMode`를 제거했다. 알러젠 신뢰도와 caution은 후보 풀에서 계산된 후보 스냅샷으로 전달한다.

### 5단계: 검색/기록 경로 정리

1. 사용자 검색은 내부 카탈로그 우선으로 고정
2. 외부 API 검색은 관리자/배치 경로로 이동
3. 사용자 커스텀 식품은 기본 추천 제외
4. 검색 결과에 브랜드/제공량/출처 표시 여부 검토

진행 메모(2026-06-12):

- iOS 6개 파일 수정, 외부 API 의존 전면 제거.
- `DietModels.swift`에서 `ExternalFoodResult`, `ImportFoodRequest`, `FoodDataSource` 삭제.
- `AddDietLogViewModel.swift`의 `DietFoodSearching` 프로토콜을 `searchFoodCatalog(query:)` 단일 메서드로 단순화.
- `FoodEntrySource.swift`에서 `externalResults` published property, `importAndAdd()`, `fetchExternal()`, 병렬 fetch 로직 제거. `searchAll()`은 카탈로그 단일 경로로 단순화.
- `AddDietLogView.swift`에서 "외부 검색" 섹션과 `ExternalFoodRow` 구조체 제거.
- `APIEndpoint.swift`에서 `searchExternalFoods`, `importExternalFood` case 제거.
- `AddDietLogViewModelTests.swift`에서 `MockDietFoodSearcher`의 `searchExternalFoods` 메서드와 `externalDelay` 파라미터 제거, `makeExternalFood()` 헬퍼 삭제.
- 백엔드 `ExternalFoodController.java`에 Phase 5 주석 추가 — 엔드포인트는 관리자 도구 전용으로 유지.
- 제거된 심볼 전수 grep 확인, 백엔드 `compileJava` 오류 없음.

### 6단계: 테스트와 운영 검증

1. importer 멱등성 테스트
2. 중복 방지 테스트
3. 추천 후보 필터링 테스트
4. 검색 성능 샘플 측정
5. API 장애 시 사용자 검색/추천 영향 없음 확인

---

## 11. 오늘 커밋과의 정합성 메모

2026-06-09 커밋에는 알러젠/기피식품 기반 추천 엔진, 추천 후보 DB 레벨 필터링, `AllergenConfidenceGate` 분리, `DietLogUseCases` 전환이 포함되어 있다.

본 문서는 해당 흐름을 유지한다. 추천 후보 DB 필터링은 2026-06-10 작업으로 `recommendation_status` 조건까지 포함하도록 확장했다. 알러젠 신뢰 레벨과 추천 적합성 상태는 서로 다른 축이다.

---

## 12. 오픈 이슈

- 실제 공공 API smoke 검증: `PUBLIC_FOOD_API_KEY` 설정 후 각 importer 수동 실행 및 응답 확인
- 운영 rate limit 값 확정: 현재 기본 0ms, 공공 API 트래픽 제한에 맞는 값으로 조정 필요
- `FoodNtrCpntDbInfo02`와 가공식품/음식 표준데이터의 실제 중복률 확인(dedup 리포트 실행 후)
- 표준데이터 2종의 라이선스/재사용 조건 근거 문서화
- 브랜드 공식 영양정보의 약관/재사용 조건 확인
- 세트 메뉴를 단일 항목으로 둘지 구성품 기반으로 분리할지 v2에서 결정
- 추천 적합성 게이트의 초기 나트륨/당류/포화지방 기준값 확정(Phase 4 런타임과 분리된 데이터 운영 작업)
- 사용자 커스텀 식품을 추천 후보로 승격할 수 있는 검수 기준 정의
- 앱 내 출처 고지 UI 위치 결정

---

## 13. 막다른 길

| 소스 | 사유 |
|---|---|
| 메뉴젠 API | 공공누리 제4유형으로 상업 서비스 사용 부적합 |
| 식약처 가공식품 품목별 DB 일부 파일 | 상업금지/변경금지 유형이면 제외 |
| 무검수 자동 크롤링 | 약관/정확도/갱신 책임 리스크가 커서 v1 제외 |
| 사용자 검색 시점 외부 API 의존 | 지연, 장애, 트래픽 제한, 재현성 문제로 핵심 경로에서 제외 |
