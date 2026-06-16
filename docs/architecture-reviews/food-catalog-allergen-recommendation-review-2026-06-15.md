# Food Catalog / Allergen Recommendation Review

작성일: 2026-06-15

리뷰 기준: `git diff dev...HEAD`

대상 브랜치: `feat/allegen-recommendation`

검증: 백엔드 `./gradlew test` 통과. iOS 빌드는 실행하지 않음.

## 요약

`food_catalog` 보강과 알러젠 기반 식단 추천의 방향은 전반적으로 좋다. 공공데이터와 브랜드 공식 메뉴 적재를 사용자 런타임에서 분리하고, 추천 후보 상태를 `SEARCH_ONLY` / `RECOMMENDABLE` / `RECOMMENDABLE_WITH_CAUTION` / `DISABLED`로 나눈 점은 운영 가능한 데이터 모델에 가깝다.

다만 현재 상태로는 iOS 연동 실패 가능성, Strict 알러젠 회피 모델 불일치, 추천 결과 기록 연결 누락이 크다. 특히 iOS API 응답 envelope와 DTO 필드명이 백엔드 계약과 맞지 않아 추천/제한 화면이 실제 앱에서 디코딩 실패할 가능성이 높다.

## Standards Review

### 하드 위반

1. 전역 ADR이 필요하다.

   이번 변경은 백엔드 API 계약, iOS 모델, 추천/제한 도메인 계약을 함께 바꾼다. `docs/adr/README.md` 기준상 "백엔드와 iOS를 함께 건드리거나 제품 동작/데이터 계약에 영향을 주는 결정", "API 계약이나 데이터 모델" 변경은 전역 ADR 대상이다. 현재 전역 ADR은 추가되지 않았다.

   관련 파일:
   - `docs/adr/README.md`
   - `ios/HealthCare/Core/Network/APIEndpoint.swift`
   - `ios/HealthCare/Features/Record/Diet/Models/DietRestrictionModels.swift`
   - `backend/src/main/java/com/healthcare/domain/diet/dto/FoodCatalogResponse.java`

2. 관리자 카탈로그 작업 경계가 흐려졌다.

   `ExternalFoodAdminController`가 `AdminOperationGuard`, `ExternalFoodSearchService`, `FoodImportService`를 직접 조합하고, import 경로에서 `@CurrentUserId`까지 받는다. `backend/CONTEXT.md`의 관리자 카탈로그 작업 정의는 `FoodCatalogAdminOperations`와 `AdminOperationGuard`를 operation 경계로 둔다. 외부 식품 검색/임포트도 같은 operation 경계로 모으는 편이 맞다.

   관련 파일:
   - `backend/src/main/java/com/healthcare/domain/diet/controller/ExternalFoodAdminController.java`
   - `backend/src/main/java/com/healthcare/domain/diet/admin/FoodCatalogAdminOperations.java`
   - `backend/CONTEXT.md`

### 판단 사항

1. iOS `RecommendedFoodEntry`가 백엔드 `caution` 필드를 모델링하지 않는다.

   ADR 0003은 추천 응답 노출에 `cautionForResponse()`를 사용하고, `RecommendedFoodEntry`는 응답용 주의 문구만 요청한다고 정한다. 현재 Swift 모델은 `caution`을 받지 않고 `allergenConfidenceLevel`만으로 주의 여부를 재계산한다. `RECOMMENDABLE_WITH_CAUTION`의 운영 주의 사유가 앱에 표시되지 않는다.

   관련 파일:
   - `backend/docs/adr/0003-recommendation-curation-module.md`
   - `ios/HealthCare/Features/Record/Diet/Models/DietRestrictionModels.swift`
   - `ios/HealthCare/Features/Record/Diet/Views/DietRecommendationView.swift`

## Spec Review

### P0: iOS API 디코딩 실패 가능성

iOS `APIClient.request`는 모든 성공 응답을 `SuccessEnvelope<T>`의 `data` 필드에서 꺼낸다. 하지만 새 제한/추천 컨트롤러는 raw JSON 또는 204 응답을 반환한다. 이 상태에서는 `listDietRestrictions`, `createDietRestriction`, `getDailyRecommendation`, `deleteDietRestriction` 호출이 앱에서 실패할 가능성이 높다.

또한 백엔드 `NutritionTargets`는 다음 필드를 반환한다.

- `calorieTarget`
- `proteinTargetG`
- `carbTargetG`
- `fatTargetG`

iOS `NutritionTargets`는 다음 필드를 기대한다.

- `tdeeKcal`
- `targetKcal`
- `proteinG`
- `carbsG`
- `fatG`

둘 중 하나를 계약에 맞춰 수정해야 한다. 기존 앱 네트워크 계층을 유지한다면 백엔드 컨트롤러는 `ApiResponse.ok(...)` envelope를 반환하고, iOS DTO는 백엔드 필드명과 맞춰야 한다.

관련 파일:
- `ios/HealthCare/Core/Network/APIClient.swift`
- `backend/src/main/java/com/healthcare/domain/diet/restriction/controller/DietRestrictionController.java`
- `backend/src/main/java/com/healthcare/domain/diet/recommendation/controller/DietRecommendationController.java`
- `backend/src/main/java/com/healthcare/domain/nutrition/dto/NutritionTargets.java`
- `ios/HealthCare/Features/Record/Diet/Models/DietRestrictionModels.swift`

### P0: 알러젠 신뢰 모델이 PRD와 다름

PRD는 신뢰 레벨을 식품 자체가 아니라 "이 알러젠이 없다"는 회피 주장에 둔다고 명시한다. 하지만 현재 `food_allergen_tags`는 "레코드가 존재하면 해당 알러젠이 식품에 포함됨"을 뜻한다. 스키마 주석도 "confidence_level은 없음 주장의 신뢰도가 아니라 검토 수준"이라고 되어 있어 PRD와 정면으로 다르다.

현재 Strict 게이트도 제한 알러젠별 "없음" 검증을 하지 않는다. 고신뢰 태그가 하나라도 있으면 통과하는 구조라, 예를 들어 우유 제한 사용자가 밀에 대해 `DIRECT_VERIFIED` 태그가 있는 식품을 통과시킬 수 있다. 이는 Strict 모드의 핵심 의도와 맞지 않는다.

관련 파일:
- `docs/product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md`
- `backend/src/main/resources/db/migration/V22__allergen_restriction_schema.sql`
- `backend/src/main/java/com/healthcare/domain/diet/allergen/AllergenConfidenceGate.java`
- `backend/src/main/resources/db/migration/V26__seed_allergen_tags.sql`

### P1: 추천 결과를 식단 기록으로 저장하는 흐름 없음

PRD는 "추천 기능의 최종 행동은 보기 아니라 기록하기"이며, 사용자는 전체 식단 또는 끼니 단위로 기존 식단 기록에 저장할 수 있어야 한다고 정한다. 현재 `DietRecommendationView`는 추천 결과를 보여주지만, 추천 끼니를 기존 식단 기록 생성 API로 저장하는 액션이 없다.

관련 파일:
- `docs/product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md`
- `ios/HealthCare/Features/Record/Diet/Views/DietRecommendationView.swift`
- `ios/HealthCare/Features/Record/Diet/ViewModels/DietRecommendationViewModel.swift`

### P1: 식품 카탈로그 선택 기반 제외 등록 없음

PRD는 식단 제한 설정에서 사용자가 알러지 태그를 선택하거나, 식품을 검색하거나, 직접 키워드를 입력할 수 있어야 한다고 정한다. 백엔드에는 `FOOD` 타입 제한이 있지만, iOS `DietRestrictionView`는 allergen/category/keyword 탭만 제공한다. 실제 사용자는 특정 식품을 검색해서 제외 조건으로 등록할 수 없다.

관련 파일:
- `backend/src/main/java/com/healthcare/domain/diet/restriction/entity/DietRestriction.java`
- `ios/HealthCare/Features/Record/Diet/Views/DietRestrictionView.swift`
- `ios/HealthCare/Features/Record/Diet/ViewModels/DietRestrictionViewModel.swift`

### P1: 추천 실패 상태 없음

PRD는 추천 후보가 부족하거나 영양 목표 계산에 필요한 정보가 부족하면 빈 화면 대신 추천 실패 이유를 보여주라고 한다. 현재 후보가 없어도 `DietRecommendationEngine`은 빈 `items`를 가진 끼니를 정상 응답으로 만들고, use case도 실패 사유를 만들지 않는다.

관련 파일:
- `backend/src/main/java/com/healthcare/domain/diet/recommendation/engine/DietRecommendationEngine.java`
- `backend/src/main/java/com/healthcare/domain/diet/recommendation/usecase/DailyDietRecommendationUseCases.java`
- `ios/HealthCare/Features/Record/Diet/Views/DietRecommendationView.swift`

### P2: 주의 추천 사유가 iOS에 표시되지 않음

`RECOMMENDABLE_WITH_CAUTION` 식품이 포함되면 주의 근거를 표시할 수 있어야 한다. 백엔드는 `RecommendedFoodEntry.caution`을 내려주지만, iOS 모델에는 `caution` 필드가 없고 화면도 문구를 표시하지 않는다.

관련 파일:
- `backend/src/main/java/com/healthcare/domain/diet/recommendation/dto/RecommendedFoodEntry.java`
- `ios/HealthCare/Features/Record/Diet/Models/DietRestrictionModels.swift`
- `ios/HealthCare/Features/Record/Diet/Views/DietRecommendationView.swift`

### 범위 확장

다음 변경은 food catalog / allergen recommendation 스펙과 직접 관련이 낮아 별도 PR로 분리하는 편이 좋다.

- GoogleSignIn 패키지/최소 버전 변경
- FCM 테스트 격리
- 체중 숫자 레이아웃 수정
- `docs/PORTFOLIO.md`
- architecture review HTML 문서 추가

## 잘된 점

1. `food_catalog`를 식단 기록 검색, 식단 기록 영양 계산, 식단 추천 후보 풀의 공통 기반으로 정리한 점이 좋다.
2. 사용자 검색/추천 시점의 외부 API 호출을 제거하고, 배치/관리자 적재 경로로 낮춘 방향이 안정적이다.
3. 공공데이터 항목을 기본 `SEARCH_ONLY`로 두고, 명시 검수 seed/브랜드 공식 메뉴만 추천 후보로 승격하는 정책이 PRD와 잘 맞는다.
4. `RecommendationCuration` 값 객체로 `RECOMMENDABLE_WITH_CAUTION` 사유 필수 조건을 묶은 점이 좋다.
5. `DietRecommendationCandidatePool`로 DB 엔티티와 알러젠 태그 맵을 추천 엔진에서 숨긴 점은 경계가 선명하다.
6. `FoodCatalogIngestService`가 source + food code 기준 upsert와 큐레이션 보존/교체 정책을 나누는 구조가 좋다.
7. seed allowlist 최소 후보 규모를 V25 마이그레이션과 테스트로 보강한 점이 운영 리스크를 낮춘다.
8. 백엔드 전체 테스트가 통과하고, importer/dedup/admin/recommendation 관련 테스트가 폭넓게 추가된 점은 긍정적이다.

## 추가 작업 제안

### 우선순위 1

1. iOS API 계약을 먼저 맞춘다.
   - 새 백엔드 컨트롤러를 `ApiResponse` envelope로 감싸거나, iOS `APIClient`에 raw response 지원을 명시적으로 추가한다.
   - `NutritionTargets` 필드명을 백엔드와 iOS 중 한쪽으로 통일한다.
   - `deleteDietRestriction`은 `requestVoid`를 사용하거나 백엔드가 `ApiResponse<Void>` 형태를 반환하도록 맞춘다.

2. 알러젠 회피 모델을 다시 결정한다.
   - PRD를 유지한다면 "제한 알러젠 없음 주장"을 저장/조회할 수 있는 별도 구조가 필요하다.
   - 현재 포함 태그 모델을 유지한다면 PRD의 Strict 의미, 디스클레이머, 수용 기준을 수정해야 한다.
   - 어떤 선택이든 Strict 모드 테스트는 제한 알러젠별 없음 검증을 기준으로 다시 작성한다.

### 우선순위 2

1. 추천 실패 상태를 API 계약에 추가한다.
   - 후보 없음, 필수 프로필 부족, 제한 조건 과다, 목표 불일치 등을 구분한다.
   - 실패 시 422 또는 성공 응답 내 실패 상태 중 하나로 계약을 확정한다.

2. 추천 결과 기록하기를 구현한다.
   - 끼니 단위 저장을 우선한다.
   - 전체 식단 저장은 여러 `DietLog` 생성이 필요하므로 실패/부분 성공 정책을 함께 정한다.

3. 식품 카탈로그 검색 기반 제한 등록을 iOS에 추가한다.
   - 기존 `getFoodCatalog` 검색을 재사용한다.
   - 선택한 식품의 이름을 제한 목록에 표시할 수 있도록 응답 DTO 또는 iOS 캐시 전략을 정한다.

4. `RECOMMENDABLE_WITH_CAUTION` 문구를 iOS에 표시한다.
   - Swift `RecommendedFoodEntry`에 `caution: String?`을 추가한다.
   - 경고 아이콘만이 아니라 실제 운영 사유 문구를 보여준다.

### 우선순위 3

1. 전역 ADR을 추가한다.
   - food catalog 공통 기반화
   - 알러젠 회피 신뢰 모델
   - iOS/백엔드 추천 API 계약
   - 추천 결과 기록 연결 방식을 기록한다.

2. `ExternalFoodAdminController`를 operation 경계로 정리한다.
   - 외부 검색/임포트도 `FoodCatalogAdminOperations` 또는 별도 `ExternalFoodAdminOperations`로 이동한다.
   - 컨트롤러는 HTTP adapter 역할만 하도록 줄인다.

3. 이번 스펙과 직접 무관한 변경을 별도 PR로 분리한다.

## 결론

총 findings:

- Standards: 3건
- Spec: 6건

가장 큰 리스크는 iOS API 디코딩 실패 가능성과 Strict 알러젠 회피 모델 불일치다. 이 두 가지를 먼저 해결해야 실제 사용자가 추천/제한 기능을 안정적으로 사용할 수 있다.
