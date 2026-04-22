# 운동 외부 데이터 연동 설계

작성일: 2026-04-22  
상태: 초안  
대상 독자: 백엔드, iOS, 기획, 아키텍처

---

## 1. 문서 목적

운동 종목 데이터와 칼로리 소모 추정 데이터를 외부에서 가져올 수 있는 여러 후보를 검토한 뒤, 우리 서비스와 어떤 방식으로 연동하는 것이 적절한지 권장안을 확정한다.

이번 문서의 핵심 목적은 아래 두 가지다.

- 외부 운동 API를 어디에 쓰고 어디에는 쓰지 않을지 명확히 한다.
- 현재 아키텍처를 유지하면서도 추후 확장 여지를 남기는 연동 원칙을 정리한다.

참고 조사 문서:

- `docs/references/EXERCISE_API_SURVEY_2026-04-22.md`

---

## 2. 현재 구조 (AS-IS)

### 2.1 백엔드 운동 기록 구조

현재 백엔드는 아래 구조를 가진다.

- `exercise_catalog`가 운동 마스터 데이터를 저장한다.
- `exercise_catalog.met_value`가 칼로리 계산 기준값 역할을 한다.
- `ExerciseSessionService.calculateCalories()`가 세션 저장 시 내부 계산을 수행한다.
- `exercise_sessions.calories_burned`와 `calorie_estimate_method`가 저장된다.

즉, 이미 우리 서비스는 "운동 기록 저장"과 "칼로리 추정 계산"을 내부 도메인 책임으로 가진다.

관련 코드:

- `backend/src/main/java/com/healthcare/domain/exercise/entity/ExerciseCatalog.java`
- `backend/src/main/java/com/healthcare/domain/exercise/service/ExerciseSessionService.java`
- `backend/src/main/resources/db/migration/V2__exercise_schema.sql`

### 2.2 iOS 운동 기록 UX

현재 iOS 흐름은 아래와 같다.

1. 내부 카탈로그를 검색한다.
2. 운동을 선택해 세트를 작성한다.
3. 세션 저장 API를 호출한다.

즉, 현재 UX는 외부 검색 기반이 아니라 내부 카탈로그 중심이다.

관련 코드:

- `ios/HealthCare/Features/Record/Exercise/ViewModels/AddExerciseSessionViewModel.swift`
- `ios/HealthCare/Features/Record/Exercise/Models/ExerciseModels.swift`

### 2.3 식단 도메인의 외부 연동 패턴

식단 도메인에는 이미 아래 패턴이 존재한다.

- 외부 API 검색
- 내부 정규화 DTO 변환
- 사용자가 선택하면 import
- 최종 기록은 내부 카탈로그 기준으로 저장

이는 운동 도메인 확장 시 참고할 수 있는 좋은 선례다.

관련 코드:

- `backend/src/main/java/com/healthcare/domain/diet/external/service/ExternalFoodSearchService.java`
- `backend/src/main/java/com/healthcare/domain/diet/external/service/FoodImportService.java`
- `backend/src/main/java/com/healthcare/domain/diet/external/controller/ExternalFoodController.java`

---

## 3. 권장 원칙

이번 문서에서 확정하는 연동 원칙은 아래와 같다.

### 3.1 System of Record

- 운동 카탈로그의 운영 기준 데이터는 내부 백엔드가 가진다.
- 칼로리 계산의 최종 책임도 내부 백엔드가 가진다.
- 외부 API는 추천 데이터 공급원이지 최종 truth source가 아니다.

### 3.2 Write Path 원칙

- 운동 세션 저장 write path에는 외부 API를 직접 넣지 않는다.
- `POST /api/v1/exercise/sessions`는 외부 API 상태와 무관하게 동작해야 한다.
- 외부 API 장애가 사용자의 운동 기록 저장 실패로 이어지지 않도록 한다.

### 3.3 UX 원칙

- 운동 검색 UX의 기본은 내부 `GET /api/v1/exercise/catalog`를 유지한다.
- 한국어 서비스 특성상 운동명 검색과 표시 체계는 내부 `name_ko` 정규화가 우선이다.
- 외부 공급자 명칭을 그대로 UI의 기준 명칭으로 사용하지 않는다.

### 3.4 계산 원칙

- 칼로리 값은 계속 `추정치`로 안내한다.
- 기준 MET 값은 `Compendium of Physical Activities`를 우선 레퍼런스로 사용한다.
- 외부 벤더가 제공하는 칼로리 값은 비교 기준 또는 참고값으로만 취급한다.

---

## 4. 권장 아키텍처 (TO-BE)

### 4.1 기본 흐름

권장 기본 흐름은 현재 구조를 유지하되, 내부 카탈로그 품질을 높이는 방향이다.

1. 사용자는 내부 운동 카탈로그를 검색한다.
2. 운동과 세트 정보를 입력한다.
3. 서버는 내부 `exercise_catalog.met_value`를 사용해 칼로리를 계산한다.
4. 계산된 값은 `calories_burned`에 저장되며, 사용자에게 추정치로 노출된다.

### 4.2 외부 데이터의 사용 위치

외부 데이터는 아래 위치에만 사용한다.

- 카탈로그 enrichment
- 초기 시드 데이터 검토
- 관리자/배치 기반 보강
- 추후 import 후보 탐색
- 검색 품질 개선 실험

외부 데이터를 아래 위치에 사용하는 것은 권장하지 않는다.

- 세션 저장 시 필수 실시간 호출
- 사용자 기록의 최종 truth 계산
- 기본 검색 UX의 1차 데이터 소스

### 4.3 권장 데이터 흐름

```text
외부 레퍼런스 / 외부 API
    -> 내부 검토 / 정규화
    -> exercise_catalog 보강
    -> 사용자 검색 / 선택
    -> exercise_sessions 저장
    -> 내부 칼로리 계산
```

핵심은 "외부 데이터는 내부로 흡수된 뒤 사용한다"는 점이다.

---

## 5. 채택안

### 5.1 MVP 기본 채택안

MVP에서는 아래 방식을 채택한다.

- 운동 검색 UX: 내부 `GET /api/v1/exercise/catalog`
- 운동 저장 UX: 기존 `POST /api/v1/exercise/sessions`
- 칼로리 계산: `ExerciseSessionService.calculateCalories()` 유지
- MET 값 관리: `Compendium` 기준으로 내부 카탈로그 값 정제
- 외부 API 사용 위치: 운영 경로가 아닌 보강 도구 또는 후속 실험

### 5.2 외부 후보별 채택 판단

#### Compendium

- 역할: 기준 MET 레퍼런스
- 채택 여부: 채택
- 쓰는 방식: 내부 `met_value` 정제 기준

#### wger

- 역할: 운동 카탈로그 보강 후보
- 채택 여부: 즉시 운영 채택 아님
- 쓰는 방식: 필요 시 관리자/배치 기반 시드 보강 검토

#### Nutritionix

- 역할: 자연어 운동 입력 실험 후보
- 채택 여부: MVP 기본 채택 아님
- 쓰는 방식: 검색 UX 실험이 필요할 때 PoC

#### API Ninjas

- 역할: 빠른 비교 실험용 칼로리/운동 API
- 채택 여부: MVP 기본 채택 아님
- 쓰는 방식: 벤더 비교 실험 또는 내부 기준값 검증 참고

---

## 6. 채택하지 않는 방향과 이유

### 6.1 실시간 외부 칼로리 API 의존

채택하지 않는다.

이유:

- 외부 장애가 기록 저장 성공 여부에 영향을 준다.
- 사용자 기록의 일관성이 벤더 상태에 흔들린다.
- 공급자 변경 시 과거/현재 계산 기준이 달라질 수 있다.

### 6.2 외부 API 결과를 세션 저장 시 truth로 채택

채택하지 않는다.

이유:

- 이미 내부 `met_value` 기반 구조가 존재한다.
- 벤더별 계산 기준 차이를 설명하기 어렵다.
- 서비스가 데이터 통제권을 잃는다.

### 6.3 한국어 운동명 UX를 외부 공급자 명칭에 직접 의존

채택하지 않는다.

이유:

- 한국어 검색 품질을 내부에서 통제하기 어렵다.
- 외부 명칭이 우리 카테고리 체계와 맞지 않을 수 있다.
- `name_ko`, muscle group, exercise type 분류의 일관성이 약해진다.

---

## 7. 단계별 로드맵

### Phase 1. 내부 카탈로그/MET 정제

목표:

- 기존 시드 운동의 `met_value`를 Compendium 기준으로 검토하고 보정한다.
- 한국어 운동명 표기 원칙을 정리한다.
- 카디오, 맨몸, 근력 운동에 대한 내부 계산 기준을 명확히 한다.

완료 기준:

- 현재 시드 운동의 MET 값 출처와 검토 기준이 문서화된다.
- 내부 카탈로그만으로도 MVP 주요 운동 검색/기록이 가능하다.

### Phase 2. 관리자/배치 기반 카탈로그 보강

목표:

- 필요 시 `wger` 또는 다른 공급원으로 운동 메타데이터를 비교 수집한다.
- 운영자가 검토 후 내부 카탈로그에 반영하는 흐름을 검토한다.

완료 기준:

- 외부 데이터를 바로 노출하지 않고 내부 정규화 후 반영하는 절차가 정리된다.

### Phase 3. 외부 검색 + import 패턴 검토

목표:

- 사용자 검색 실패가 실제 문제로 확인될 때만 운동 외부 검색/import를 검토한다.
- 식단 도메인의 external search/import 패턴을 운동 도메인에 맞게 변형한다.

가능한 확장 후보:

- `GET /api/v1/exercise/external-search?q=&provider=`
- `POST /api/v1/exercise/external-import`
- `ExternalExerciseSearchClient`
- `ExternalExerciseResult`
- `ExerciseImportService`

주의:

- 이 단계는 MVP 필수가 아니다.
- 실제 검색 실패 데이터와 운영 가치가 확인되기 전에는 구현하지 않는다.

---

## 8. 공개 API/인터페이스 영향

이번 결정으로 즉시 바뀌는 공개 API는 없다.

유지 대상:

- `GET /api/v1/exercise/catalog`
- `POST /api/v1/exercise/catalog`
- `POST /api/v1/exercise/sessions`

즉, 이번 작업은 코드 계약 변경이 아니라 문서화와 연동 원칙 확정이다.

미래 확장용 초안은 아래 정도만 참고 수준으로 유지한다.

```text
GET  /api/v1/exercise/external-search?q=&provider=
POST /api/v1/exercise/external-import

interface ExternalExerciseSearchClient
record ExternalExerciseResult
class ExerciseImportService
```

이 초안은 "필요 시 도입"이며 현재 구현 범위로 승격하지 않는다.

---

## 9. 예시 시나리오

### 시나리오 A. 기본 운동 기록

- 사용자가 `벤치 프레스`, `스쿼트`, `트레드밀 달리기`를 내부 카탈로그에서 검색한다.
- 세트와 시간을 입력한다.
- 서버는 내부 MET 기준으로 칼로리를 계산해 저장한다.
- 앱은 칼로리를 추정치로 보여준다.

### 시나리오 B. 내부 카탈로그에 없는 운동

- MVP에서는 사용자가 커스텀 운동을 생성한다.
- 이후 검색 실패 데이터가 충분히 누적되면 외부 검색/import를 검토한다.

### 시나리오 C. 외부 서비스별 칼로리 값 차이

- 같은 운동이라도 벤더마다 칼로리 값이 달라질 수 있다.
- 우리 서비스는 벤더 값을 직접 truth로 저장하지 않는다.
- 내부 기준식을 유지하고, 사용자에게는 추정치라는 점을 일관되게 안내한다.

---

## 10. 최종 권장 결론

우리 서비스는 외부 운동 API를 "운영 핵심 경로"에 넣기보다 "내부 카탈로그를 강화하는 보조 수단"으로 다루는 것이 가장 적절하다.

정리하면 아래와 같다.

- 운동 세션 저장과 칼로리 계산의 system of record는 계속 내부 백엔드가 맡는다.
- `Compendium`를 기준 MET 레퍼런스로 채택한다.
- `wger`는 카탈로그 보강 후보로만 본다.
- `Nutritionix`, `API Ninjas`는 빠른 PoC 또는 비교 실험용 후보로만 유지한다.
- MVP에서는 외부 검색/import를 열지 않고, 내부 카탈로그와 커스텀 운동 생성으로 충분히 대응한다.

이 방향이 현재 제품 목표와 가장 잘 맞는다.

- 빠른 입력
- 한국어 중심 UX
- 일관된 추정 기준
- 내부 데이터 통제
- 운영 안정성
