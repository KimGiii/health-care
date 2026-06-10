# 식품 카탈로그 기술 가이드

**작성일**: 2026-05-04

**개정일**: 2026-06-10

**상태**: MVP 구현 완료 / Phase 1 스키마 + 추천 후보 필터 + Phase 2 배치 파이프라인·중복 리포터·관리자 API 구현

**작업 브랜치**: `feat/allegen-recommendation`

알러젠 식단 추천과 식품 카탈로그 강화 작업은 `feat/allegen-recommendation` 브랜치에서만 진행합니다. `dev`에는 직접 커밋하지 않고, 검증된 변경을 PR/머지로 반영합니다.

## 개요

HealthCare 앱의 식품 카탈로그는 공공 데이터(식품디비) + 사용자 커스텀 식품의 조합으로 운영됩니다. 이 문서는 2026-05-04에 구현된 **사용 횟수 추적** 및 **사용자 직접 등록** 기능을 설명합니다.

2026-06-09 기준으로 식품 카탈로그 강화 방향이 확정되었습니다. 2026-06-10에는 V23 스키마 보강과 추천 후보 필터(Phase 1)에 이어, 공공데이터 배치 적재 파이프라인, 동일 추정 중복 후보 리포터, 관리자 실행 API(Phase 2)가 완료되었습니다.

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

백필 기본값:

- 기존 시드 식품: `source = SEED`, `recommendation_status = RECOMMENDABLE`
- 기존 사용자 커스텀 식품: `source = USER_CUSTOM`, `recommendation_status = SEARCH_ONLY`

추가 인덱스:

```sql
CREATE UNIQUE INDEX uq_food_catalog_source_food_code
  ON food_catalog (source, food_code)
  WHERE food_code IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_food_catalog_recommendation_status
  ON food_catalog (recommendation_status)
  WHERE deleted_at IS NULL;
```

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
├── recommendation/usecase/DailyDietRecommendationUseCases.java # 추천 후보 상태 필터 적용
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
- `food_code`, 식품명, 열량이 없거나 파싱할 수 없으면 skip 처리합니다.
- 공공데이터로 들어온 항목은 기본 `SEARCH_ONLY`로 저장합니다. 추천 후보 승격은 별도 추천 적합성 게이트에서 다룹니다.
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

자동 병합은 하지 않습니다. 그룹 정보(`normalizedKey`, 항목 목록)만 반환하며, 병합 여부는 운영자가 직접 판단합니다.

#### 관리자 API 엔드포인트

`FoodCatalogAdminController` — `/api/v1/admin/diet/catalog`

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/import/processed-foods` | 가공식품 표준데이터(15100066) 배치 적재 |
| `POST` | `/import/dish-foods` | 음식 표준데이터(15100070) 배치 적재 |
| `POST` | `/import/nutrient-db` | 식품영양성분DB(`FoodNtrCpntDbInfo02`) 배치 적재 |
| `GET` | `/dedup/report` | 비커스텀 카탈로그 전체 대상 중복 후보 리포트 |

import 엔드포인트는 `pageSize`(기본 100)와 `maxPages`(기본 500) 쿼리 파라미터를 받습니다. 응답은 `FoodCatalogBatchImportSummary`(source, startPage, lastCompletedPage, created/updated/skipped 수, exhausted 여부)를 포함합니다.

dedup 리포트 응답 예시:

```json
{
  "totalGroups": 3,
  "totalCandidates": 7,
  "groups": [
    {
      "normalizedKey": "닭가슴살",
      "count": 3,
      "entries": [
        { "id": 1, "name": "닭가슴살", "source": "SEED", "caloriesPer100g": 165.0, ... },
        { "id": 42, "name": "닭 가슴살", "source": "MFDS_FOOD_NUTRIENT_DB", ... },
        { "id": 87, "name": "닭가슴살(구운)", "source": "MFDS_STANDARD_PROCESSED", ... }
      ]
    }
  ]
}
```

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
- FoodCatalogSpecsTest: isRecommendable_includesOnlyRecommendableStatuses()
- DailyDietRecommendationUseCasesTest: loadCandidates_alwaysAppliesRecommendationStatusFilter()
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
   - 기존 시드 식품은 추천 후보 유지를 위해 `RECOMMENDABLE`로 백필됩니다.
   - 기존 사용자 커스텀 식품은 검증 전 추천 제외를 위해 `SEARCH_ONLY`로 백필됩니다.

3. **V24 마이그레이션 실행**
   - Flyway가 `V24__food_catalog_import_checkpoints.sql`을 자동 적용합니다.
   - 공공데이터 배치 적재 시 source별 마지막 완료 페이지를 추적하는 `food_catalog_import_checkpoints` 테이블이 생성됩니다.

3. **초기 usage_count 계산 (선택 사항)**
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

3. **공공데이터 사전 적재 파이프라인 구현 완료, 실제 실행 대기**
   - 배치 파이프라인(importer, fetcher, runner, checkpoint) 구현 완료
   - `POST /api/v1/admin/diet/catalog/import/*` 관리자 API로 실행 가능
   - 실제 실행 전 `PUBLIC_FOOD_API_KEY` 환경 변수 설정 필요
   - 5단계(검색/기록 경로 정리)에서 사용자 검색 경로의 외부 API 의존을 내부 DB 기준으로 고정 예정

4. **주의 상태 추천 응답 모델 미확정**
   - `RECOMMENDABLE_WITH_CAUTION`은 추천 후보에 포함되지만, 추천 응답에서 `recommendation_reason`을 어떤 형태로 노출할지는 아직 확정되지 않음
   - 향후: 끼니 추천 응답 모델에 주의 사유/출처 표시 여부 검토

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
